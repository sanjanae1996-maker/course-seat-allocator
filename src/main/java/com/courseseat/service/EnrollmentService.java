package com.courseseat.service;

import com.courseseat.model.Course;
import com.courseseat.model.EnrollResult;
import com.courseseat.model.EnrollStatus;
import com.courseseat.model.Learner;
import com.courseseat.repository.LearnerRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Enrollment state and rules for one course offering. */
public class EnrollmentService {

    private final Course course;
    private final LearnerRepository learners;

    private final Set<String> enrolled = new LinkedHashSet<>();
    private final Set<String> waitlist = new LinkedHashSet<>();

    public EnrollmentService(Course course, LearnerRepository learners) {
        this.course = Objects.requireNonNull(course, "course");
        this.learners = Objects.requireNonNull(learners, "learners");
    }

    /** Applies the checks in DESIGN.md section 5, in order; the first match returns. */
    public EnrollResult enroll(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return new EnrollResult(EnrollStatus.INVALID_REQUEST, learnerId);
        }
        Optional<Learner> learner = learners.find(learnerId);
        if (learner.isEmpty()) {
            return new EnrollResult(EnrollStatus.UNKNOWN_LEARNER, learnerId);
        }
        if (enrolled.contains(learnerId)) {
            return new EnrollResult(EnrollStatus.ALREADY_ENROLLED, learnerId);
        }
        if (waitlist.contains(learnerId)) {
            return new EnrollResult(EnrollStatus.ALREADY_WAITLISTED, learnerId);
        }
        if (!isEligible(learner.get())) {
            return new EnrollResult(EnrollStatus.PREREQ_NOT_MET, learnerId);
        }
        if (enrolled.size() < course.capacity()) {
            enrolled.add(learnerId);
            return new EnrollResult(EnrollStatus.ENROLLED, learnerId);
        }
        if (waitlist.size() < course.maxWaitlistSize()) {
            waitlist.add(learnerId);
            return new EnrollResult(EnrollStatus.WAITLISTED, learnerId);
        }
        return new EnrollResult(EnrollStatus.WAITLIST_FULL, learnerId);
    }

    public List<String> getEnrolled() {
        return List.copyOf(enrolled);
    }

    public List<String> getWaitlist() {
        return List.copyOf(waitlist);
    }

    private boolean isEligible(Learner learner) {
        return learner.completedCourseIds().containsAll(course.requiredCourseIds());
    }
}
