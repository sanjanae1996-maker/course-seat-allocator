package com.courseseat.model;

import java.util.Set;

/** One course offering: its seat limit, waitlist cap and prerequisites. */
public record Course(String id, int capacity, int maxWaitlistSize, Set<String> requiredCourseIds) {

    public Course {
        requiredCourseIds = Set.copyOf(requiredCourseIds);
    }
}
