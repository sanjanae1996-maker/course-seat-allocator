package com.courseseat.service;

import com.courseseat.model.Course;
import com.courseseat.model.DropResult;
import com.courseseat.model.DropStatus;
import com.courseseat.model.EnrollResult;
import com.courseseat.model.EnrollStatus;
import com.courseseat.model.Learner;
import com.courseseat.repository.LearnerRepository;

import java.util.ArrayList;
import java.util.Iterator;
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

    /** Applies the checks in DESIGN.md section 6, in order; the first match returns. */
    public DropResult drop(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return withoutPromotion(DropStatus.INVALID_REQUEST, learnerId);
        }
        if (learners.find(learnerId).isEmpty()) {
            return withoutPromotion(DropStatus.UNKNOWN_LEARNER, learnerId);
        }
        if (waitlist.remove(learnerId)) {
            return withoutPromotion(DropStatus.REMOVED_FROM_WAITLIST, learnerId);
        }
        if (!enrolled.remove(learnerId)) {
            return withoutPromotion(DropStatus.NOT_ENROLLED, learnerId);
        }
        return promoteNext(learnerId);
    }

    public List<String> getEnrolled() {
        return List.copyOf(enrolled);
    }

    public List<String> getWaitlist() {
        return List.copyOf(waitlist);
    }

    /*
     * Called from drop, so the freed seat goes to the waitlist before any new enroll
     * request can take it. Waiting learners who are no longer eligible are removed and
     * reported as skipped (skip policy, DESIGN.md section 6).
     */
    private DropResult promoteNext(String droppedId) {
        List<String> skipped = new ArrayList<>();
        Iterator<String> front = waitlist.iterator();
        while (front.hasNext()) {
            String candidateId = front.next();
            front.remove();
            // Read the current record: it may have changed while the learner waited.
            Optional<Learner> candidate = learners.find(candidateId);
            if (candidate.isPresent() && isEligible(candidate.get())) {
                enrolled.add(candidateId);
                return new DropResult(DropStatus.DROPPED, droppedId, Optional.of(candidateId), skipped);
            }
            skipped.add(candidateId);
        }
        return new DropResult(DropStatus.DROPPED, droppedId, Optional.empty(), skipped);
    }

    private static DropResult withoutPromotion(DropStatus status, String learnerId) {
        return new DropResult(status, learnerId, Optional.empty(), List.of());
    }

    private boolean isEligible(Learner learner) {
        return learner.completedCourseIds().containsAll(course.requiredCourseIds());
    }
}
