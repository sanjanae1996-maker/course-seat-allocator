package com.courseseat.model;

import java.util.List;
import java.util.Optional;

/**
 * Result of a drop request, describing every state change it caused:
 * who was promoted into the freed seat, and who was skipped as ineligible.
 */
public record DropResult(DropStatus status, String droppedId, Optional<String> promotedId, List<String> skippedIds) {

    public DropResult {
        skippedIds = List.copyOf(skippedIds);
    }
}
