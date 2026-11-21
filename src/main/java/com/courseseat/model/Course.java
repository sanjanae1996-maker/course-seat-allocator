package com.courseseat.model;

import java.util.Set;

/** One course offering: its seat limit, waitlist cap and prerequisites. */
public record Course(String id, int capacity, int maxWaitlistSize, Set<String> requiredCourseIds) {

    public Course {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        if (maxWaitlistSize < 0) {
            throw new IllegalArgumentException("maxWaitlistSize must not be negative");
        }
        if (requiredCourseIds == null) {
            throw new IllegalArgumentException("requiredCourseIds must not be null");
        }
        requiredCourseIds = Set.copyOf(requiredCourseIds);
    }
}
