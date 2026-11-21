package com.courseseat.repository;

import com.courseseat.model.Learner;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnerRepositoryTest {

    private final LearnerRepository repository = new LearnerRepository();

    @Test
    void findsSavedLearner() {
        Learner learner = new Learner("L001", "Asha", Set.of("CS101"));

        repository.save(learner);

        assertEquals(Optional.of(learner), repository.find("L001"));
    }

    @Test
    void unknownIdReturnsEmpty() {
        assertTrue(repository.find("L999").isEmpty());
        assertTrue(repository.find(null).isEmpty());
    }

    @Test
    void savingAgainReplacesLearner() {
        repository.save(new Learner("L001", "Asha", Set.of("CS101")));
        Learner revoked = new Learner("L001", "Asha", Set.of());

        repository.save(revoked);

        assertEquals(Optional.of(revoked), repository.find("L001"));
    }
}
