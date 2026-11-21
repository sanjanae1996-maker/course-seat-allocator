package com.courseseat.repository;

import com.courseseat.model.Learner;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory store of learners, keyed by learner ID. */
public class LearnerRepository {

    // Concurrent map: safe when updated outside the service lock (e.g. by tests).
    private final Map<String, Learner> store = new ConcurrentHashMap<>();

    public Optional<Learner> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }

    /** Inserts the learner, or replaces the existing record with the same ID. */
    public void save(Learner learner) {
        store.put(learner.id(), learner);
    }
}
