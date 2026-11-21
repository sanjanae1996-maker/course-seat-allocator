package com.courseseat;

import com.courseseat.model.Course;
import com.courseseat.model.Learner;
import com.courseseat.repository.LearnerRepository;

import java.util.Set;

/** Small fixtures shared by the tests. */
public final class TestData {

    public static final String PREREQ = "CS101";

    private TestData() {
    }

    /** A course that requires CS101. */
    public static Course course(int capacity, int maxWaitlist) {
        return new Course("CS201", capacity, maxWaitlist, Set.of(PREREQ));
    }

    /** A learner who has completed CS101. */
    public static Learner eligible(String id) {
        return new Learner(id, "Learner " + id, Set.of(PREREQ));
    }

    /** A learner who has completed some other course, but not CS101. */
    public static Learner ineligible(String id) {
        return new Learner(id, "Learner " + id, Set.of("MATH100"));
    }

    /** The same learner with no completed courses; save it to revoke CS101 while they wait. */
    public static Learner revoked(String id) {
        return new Learner(id, "Learner " + id, Set.of());
    }

    public static LearnerRepository repositoryWith(Learner... learners) {
        LearnerRepository repository = new LearnerRepository();
        for (Learner learner : learners) {
            repository.save(learner);
        }
        return repository;
    }
}
