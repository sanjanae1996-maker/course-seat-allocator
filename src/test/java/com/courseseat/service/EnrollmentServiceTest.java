package com.courseseat.service;

import com.courseseat.model.DropResult;
import com.courseseat.model.DropStatus;
import com.courseseat.model.EnrollResult;
import com.courseseat.model.EnrollStatus;
import com.courseseat.repository.LearnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.courseseat.TestData.course;
import static com.courseseat.TestData.eligible;
import static com.courseseat.TestData.ineligible;
import static com.courseseat.TestData.repositoryWith;
import static com.courseseat.TestData.revoked;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EnrollmentServiceTest {

    private LearnerRepository learners;
    private EnrollmentService service;

    @BeforeEach
    void setUp() {
        learners = repositoryWith(eligible("L001"), eligible("L002"), eligible("L003"), ineligible("L004"));
        service = new EnrollmentService(course(2, 1), learners);
    }

    @Nested
    class Enroll {

        @Test
        void enrollsWhenSeatAvailable() {
            EnrollResult result = service.enroll("L001");

            assertEquals(new EnrollResult(EnrollStatus.ENROLLED, "L001"), result);
            assertEquals(List.of("L001"), service.getEnrolled());
            assertEquals(List.of(), service.getWaitlist());
        }

        @Test
        void rejectsWhenPrereqNotMet() {
            EnrollResult result = service.enroll("L004");

            assertEquals(new EnrollResult(EnrollStatus.PREREQ_NOT_MET, "L004"), result);
            assertEquals(List.of(), service.getEnrolled());
            assertEquals(List.of(), service.getWaitlist());
        }

        @Test
        void rejectsDuplicateEnrollment() {
            service.enroll("L001");

            EnrollResult result = service.enroll("L001");

            assertEquals(new EnrollResult(EnrollStatus.ALREADY_ENROLLED, "L001"), result);
            assertEquals(List.of("L001"), service.getEnrolled());
            assertEquals(List.of(), service.getWaitlist());
        }

        @Test
        void rejectsUnknownLearner() {
            EnrollResult result = service.enroll("L999");

            assertEquals(new EnrollResult(EnrollStatus.UNKNOWN_LEARNER, "L999"), result);
            assertEquals(List.of(), service.getEnrolled());
            assertEquals(List.of(), service.getWaitlist());
        }

        @Test
        void rejectsNullOrBlankId() {
            service.enroll("L001");

            assertEquals(EnrollStatus.INVALID_REQUEST, service.enroll(null).status());
            assertEquals(EnrollStatus.INVALID_REQUEST, service.enroll("").status());
            assertEquals(EnrollStatus.INVALID_REQUEST, service.enroll("   ").status());
            assertEquals(List.of("L001"), service.getEnrolled());
            assertEquals(List.of(), service.getWaitlist());
        }
    }

    @Nested
    class Waitlist {

        @Test
        void waitlistsWhenCourseFull() {
            service.enroll("L001");
            service.enroll("L002");

            EnrollResult result = service.enroll("L003");

            assertEquals(new EnrollResult(EnrollStatus.WAITLISTED, "L003"), result);
            assertEquals(List.of("L001", "L002"), service.getEnrolled());
            assertEquals(List.of("L003"), service.getWaitlist());
        }

        @Test
        void keepsFifoOrder() {
            learners.save(eligible("L005"));
            EnrollmentService fifo = new EnrollmentService(course(1, 3), learners);
            fifo.enroll("L001");

            // Arrival order deliberately differs from ID order.
            assertEquals(EnrollStatus.WAITLISTED, fifo.enroll("L005").status());
            assertEquals(EnrollStatus.WAITLISTED, fifo.enroll("L002").status());
            assertEquals(EnrollStatus.WAITLISTED, fifo.enroll("L003").status());

            assertEquals(List.of("L001"), fifo.getEnrolled());
            assertEquals(List.of("L005", "L002", "L003"), fifo.getWaitlist());
        }

        @Test
        void rejectsDuplicateWaitlistRequest() {
            // Waitlist cap 3, so a wrong move to the back would be visible.
            EnrollmentService roomy = new EnrollmentService(course(1, 3), learners);
            roomy.enroll("L001");
            roomy.enroll("L002");
            roomy.enroll("L003");

            EnrollResult result = roomy.enroll("L002");

            assertEquals(new EnrollResult(EnrollStatus.ALREADY_WAITLISTED, "L002"), result);
            assertEquals(List.of("L001"), roomy.getEnrolled());
            assertEquals(List.of("L002", "L003"), roomy.getWaitlist());
        }

        @Test
        void ineligibleLearnerNotWaitlisted() {
            service.enroll("L001");
            service.enroll("L002");
            service.enroll("L003");

            EnrollResult result = service.enroll("L004");

            // Course and waitlist are both full, but the prerequisite answer wins.
            assertEquals(new EnrollResult(EnrollStatus.PREREQ_NOT_MET, "L004"), result);
            assertEquals(List.of("L001", "L002"), service.getEnrolled());
            assertEquals(List.of("L003"), service.getWaitlist());
        }

        @Test
        void returnsWaitlistFullAtCap() {
            learners.save(eligible("L005"));
            service.enroll("L001");
            service.enroll("L002");
            service.enroll("L003");

            EnrollResult result = service.enroll("L005");

            assertEquals(new EnrollResult(EnrollStatus.WAITLIST_FULL, "L005"), result);
            assertEquals(List.of("L001", "L002"), service.getEnrolled());
            assertEquals(List.of("L003"), service.getWaitlist());
        }
    }

    @Nested
    class Drop {

        @Test
        void dropPromotesEarliestEligible() {
            EnrollmentService roomy = new EnrollmentService(course(1, 3), learners);
            roomy.enroll("L001");
            roomy.enroll("L002");
            roomy.enroll("L003");

            DropResult result = roomy.drop("L001");

            assertEquals(new DropResult(DropStatus.DROPPED, "L001", Optional.of("L002"), List.of()), result);
            assertEquals(List.of("L002"), roomy.getEnrolled());
            assertEquals(List.of("L003"), roomy.getWaitlist());
        }

        @Test
        void skipsIneligibleLearnerAtFront() {
            EnrollmentService roomy = new EnrollmentService(course(1, 3), learners);
            roomy.enroll("L001");
            roomy.enroll("L002");
            roomy.enroll("L003");
            learners.save(revoked("L002"));

            DropResult result = roomy.drop("L001");

            assertEquals(new DropResult(DropStatus.DROPPED, "L001", Optional.of("L003"), List.of("L002")), result);
            assertEquals(List.of("L003"), roomy.getEnrolled());
            assertEquals(List.of(), roomy.getWaitlist());
        }

        @Test
        void seatStaysOpenWhenNoneEligible() {
            EnrollmentService roomy = new EnrollmentService(course(1, 3), learners);
            roomy.enroll("L001");
            roomy.enroll("L002");
            learners.save(revoked("L002"));

            DropResult result = roomy.drop("L001");

            assertEquals(new DropResult(DropStatus.DROPPED, "L001", Optional.empty(), List.of("L002")), result);
            assertEquals(List.of(), roomy.getEnrolled());
            assertEquals(List.of(), roomy.getWaitlist());

            // The seat was left open, so the next eligible learner gets it directly.
            assertEquals(new EnrollResult(EnrollStatus.ENROLLED, "L003"), roomy.enroll("L003"));
            assertEquals(List.of("L003"), roomy.getEnrolled());
            assertEquals(List.of(), roomy.getWaitlist());
        }

        @Test
        void waitlistedLearnerCanLeave() {
            service.enroll("L001");
            service.enroll("L002");
            service.enroll("L003");

            DropResult result = service.drop("L003");

            assertEquals(new DropResult(DropStatus.REMOVED_FROM_WAITLIST, "L003", Optional.empty(), List.of()),
                    result);
            assertEquals(List.of("L001", "L002"), service.getEnrolled());
            assertEquals(List.of(), service.getWaitlist());
        }

        @Test
        void repeatedDropReturnsNotEnrolled() {
            learners.save(eligible("L005"));
            service.enroll("L001");
            service.enroll("L002");

            DropResult first = service.drop("L001");
            DropResult second = service.drop("L001");
            DropResult neverEnrolled = service.drop("L005");

            assertEquals(new DropResult(DropStatus.DROPPED, "L001", Optional.empty(), List.of()), first);
            assertEquals(new DropResult(DropStatus.NOT_ENROLLED, "L001", Optional.empty(), List.of()), second);
            assertEquals(new DropResult(DropStatus.NOT_ENROLLED, "L005", Optional.empty(), List.of()),
                    neverEnrolled);
            assertEquals(List.of("L002"), service.getEnrolled());
            assertEquals(List.of(), service.getWaitlist());
        }

        @Test
        void unknownLearnerDropIsRejected() {
            service.enroll("L001");

            assertEquals(new DropResult(DropStatus.UNKNOWN_LEARNER, "L999", Optional.empty(), List.of()),
                    service.drop("L999"));
            assertEquals(DropStatus.INVALID_REQUEST, service.drop(null).status());
            assertEquals(DropStatus.INVALID_REQUEST, service.drop("").status());
            assertEquals(DropStatus.INVALID_REQUEST, service.drop("   ").status());
            assertEquals(List.of("L001"), service.getEnrolled());
            assertEquals(List.of(), service.getWaitlist());
        }
    }
}
