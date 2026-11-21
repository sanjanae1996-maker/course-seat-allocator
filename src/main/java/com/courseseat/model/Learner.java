package com.courseseat.model;

import java.util.Set;

/** A learner and the IDs of the courses they have completed. */
public record Learner(String id, String name, Set<String> completedCourseIds) {

    public Learner {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (completedCourseIds == null) {
            throw new IllegalArgumentException("completedCourseIds must not be null");
        }
        completedCourseIds = Set.copyOf(completedCourseIds);
    }
}
