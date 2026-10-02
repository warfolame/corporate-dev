package io.github.warfolame.moveorder.domain;

/**
 * Lifecycle of a move order: REQUESTED -> SCHEDULED -> IN_PROGRESS -> COMPLETED.
 */
public enum MoveOrderStatus {

    /** Customer asked for a move; no crew or date yet. */
    REQUESTED,

    /** Crew and date are assigned. */
    SCHEDULED,

    /** Crew arrived and the move has started. */
    IN_PROGRESS,

    /** Move is finished and paid; the order is closed. */
    COMPLETED
}
