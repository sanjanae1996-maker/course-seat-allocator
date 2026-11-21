package com.courseseat.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CourseTest {

    @Test
    void validCourseIsCreated() {
        Course course = new Course("CS201", 5, 2, Set.of("CS101"));

        assertEquals("CS201", course.id());
        assertEquals(5, course.capacity());
        assertEquals(2, course.maxWaitlistSize());
        assertEquals(Set.of("CS101"), course.requiredCourseIds());
    }

    @Test
    void requiredCourseIdsCannotBeChangedFromOutside() {
        Set<String> required = new HashSet<>(Set.of("CS101"));
        Course course = new Course("CS201", 5, 2, required);

        required.add("CS102");

        assertEquals(Set.of("CS101"), course.requiredCourseIds());
        assertThrows(UnsupportedOperationException.class, () -> course.requiredCourseIds().add("CS102"));
    }
}
