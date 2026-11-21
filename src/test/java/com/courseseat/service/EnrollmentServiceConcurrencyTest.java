package com.courseseat.service;

import com.courseseat.model.DropResult;
import com.courseseat.model.DropStatus;
import com.courseseat.model.EnrollResult;
import com.courseseat.model.EnrollStatus;
import com.courseseat.repository.LearnerRepository;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.courseseat.TestData.course;
import static com.courseseat.TestData.repositoryWithEligible;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnrollmentServiceConcurrencyTest {

    private static final long TIMEOUT_SECONDS = 5;

    @RepeatedTest(100)
    void simultaneousEnrollmentsDoNotOverbookCourse() throws Exception {
        LearnerRepository learners = repositoryWithEligible(10);
        EnrollmentService service = new EnrollmentService(course(5, 2), learners);
        List<Callable<EnrollResult>> requests = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String id = "L" + (100 + i);
            requests.add(() -> service.enroll(id));
        }

        List<EnrollResult> results = runAtSameMoment(requests);

        assertEquals(5, countStatus(results, EnrollStatus.ENROLLED));
        assertEquals(2, countStatus(results, EnrollStatus.WAITLISTED));
        assertEquals(3, countStatus(results, EnrollStatus.WAITLIST_FULL));

        List<String> enrolled = service.getEnrolled();
        List<String> waitlist = service.getWaitlist();
        assertEquals(5, new HashSet<>(enrolled).size());
        assertEquals(2, new HashSet<>(waitlist).size());
        assertTrue(Collections.disjoint(enrolled, waitlist));
        // The state agrees with what each learner was told.
        assertEquals(idsWithStatus(results, EnrollStatus.ENROLLED), new HashSet<>(enrolled));
        assertEquals(idsWithStatus(results, EnrollStatus.WAITLISTED), new HashSet<>(waitlist));
    }

    @RepeatedTest(100)
    void sameLearnerEnrollingTwiceAtOnceIsEnrolledOnce() throws Exception {
        LearnerRepository learners = repositoryWithEligible(1);
        EnrollmentService service = new EnrollmentService(course(5, 2), learners);
        List<Callable<EnrollResult>> requests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            requests.add(() -> service.enroll("L101"));
        }

        List<EnrollResult> results = runAtSameMoment(requests);

        assertEquals(1, countStatus(results, EnrollStatus.ENROLLED));
        assertEquals(9, countStatus(results, EnrollStatus.ALREADY_ENROLLED));
        assertEquals(List.of("L101"), service.getEnrolled());
        assertEquals(List.of(), service.getWaitlist());
    }

    @RepeatedTest(100)
    void simultaneousDropsGiveEachSeatToDifferentLearner() throws Exception {
        LearnerRepository learners = repositoryWithEligible(4);
        EnrollmentService service = new EnrollmentService(course(2, 2), learners);
        service.enroll("L101");
        service.enroll("L102");
        service.enroll("L103");
        service.enroll("L104");
        assertEquals(List.of("L101", "L102"), service.getEnrolled());
        assertEquals(List.of("L103", "L104"), service.getWaitlist());

        List<DropResult> results = runAtSameMoment(List.of(
                () -> service.drop("L101"),
                () -> service.drop("L102")));

        List<String> promoted = new ArrayList<>();
        for (DropResult result : results) {
            assertEquals(DropStatus.DROPPED, result.status());
            assertEquals(List.of(), result.skippedIds());
            promoted.add(result.promotedId().orElseThrow());
        }
        // Two promotions and two distinct IDs: nobody was promoted twice.
        assertEquals(2, promoted.size());
        assertEquals(Set.of("L103", "L104"), new HashSet<>(promoted));
        assertEquals(Set.of("L103", "L104"), new HashSet<>(service.getEnrolled()));
        assertEquals(2, service.getEnrolled().size());
        assertEquals(List.of(), service.getWaitlist());
    }

    /**
     * Runs every task on its own thread and releases them all at once. Each thread first
     * reports ready, then waits at the start gate, so the calls really overlap. Timeouts
     * make a deadlock fail the test instead of hanging it.
     */
    private static <T> List<T> runAtSameMoment(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "threads did not start in time");
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static long countStatus(List<EnrollResult> results, EnrollStatus status) {
        return results.stream().filter(r -> r.status() == status).count();
    }

    private static Set<String> idsWithStatus(List<EnrollResult> results, EnrollStatus status) {
        Set<String> ids = new HashSet<>();
        for (EnrollResult result : results) {
            if (result.status() == status) {
                ids.add(result.learnerId());
            }
        }
        return ids;
    }
}
