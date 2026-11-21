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
    void rejectsNullOrBlankId() {
        assertThrows(IllegalArgumentException.class, () -> new Course(null, 5, 2, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Course("", 5, 2, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Course("  ", 5, 2, Set.of()));
    }

    @Test
    void rejectsCapacityBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new Course("CS201", 0, 2, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Course("CS201", -1, 2, Set.of()));
    }

    @Test
    void rejectsNegativeWaitlistSize() {
        assertThrows(IllegalArgumentException.class, () -> new Course("CS201", 5, -1, Set.of()));
    }

    @Test
    void rejectsNullRequiredCourseIds() {
        assertThrows(IllegalArgumentException.class, () -> new Course("CS201", 5, 2, null));
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
