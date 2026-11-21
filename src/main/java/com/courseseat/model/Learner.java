package com.courseseat.model;

import java.util.Set;

/** A learner and the IDs of the courses they have completed. */
public record Learner(String id, String name, Set<String> completedCourseIds) {

    public Learner {
        completedCourseIds = Set.copyOf(completedCourseIds);
    }
}
