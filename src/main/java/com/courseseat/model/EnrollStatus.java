package com.courseseat.model;

/** Outcomes of an enroll request (DESIGN.md section 5). */
public enum EnrollStatus {
    ENROLLED,
    WAITLISTED,
    ALREADY_ENROLLED,
    ALREADY_WAITLISTED,
    WAITLIST_FULL,
    PREREQ_NOT_MET,
    UNKNOWN_LEARNER,
    INVALID_REQUEST
}
