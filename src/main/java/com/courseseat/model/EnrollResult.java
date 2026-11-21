package com.courseseat.model;

/** Result of an enroll request. */
public record EnrollResult(EnrollStatus status, String learnerId) {
}
