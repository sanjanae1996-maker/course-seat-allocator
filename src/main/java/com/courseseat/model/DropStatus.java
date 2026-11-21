package com.courseseat.model;

/** Outcomes of a drop request (DESIGN.md section 6). */
public enum DropStatus {
    DROPPED,
    REMOVED_FROM_WAITLIST,
    NOT_ENROLLED,
    UNKNOWN_LEARNER,
    INVALID_REQUEST
}
