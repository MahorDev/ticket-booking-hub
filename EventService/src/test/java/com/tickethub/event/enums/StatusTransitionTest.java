package com.tickethub.event.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusTransitionTest {

    @Test
    void event_followsDraftPublishedCancelled() {
        assertTrue(EventStatus.DRAFT.canMoveTo(EventStatus.PUBLISHED));
        assertTrue(EventStatus.PUBLISHED.canMoveTo(EventStatus.CANCELLED));
    }

    @Test
    void event_rejectsInvalidMoves() {
        assertFalse(EventStatus.DRAFT.canMoveTo(EventStatus.CANCELLED));
        assertFalse(EventStatus.PUBLISHED.canMoveTo(EventStatus.DRAFT));
        assertFalse(EventStatus.CANCELLED.canMoveTo(EventStatus.PUBLISHED));
    }

    @Test
    void show_followsScheduledOpenThenEnds() {
        assertTrue(ShowStatus.SCHEDULED.canMoveTo(ShowStatus.OPEN));
        assertTrue(ShowStatus.SCHEDULED.canMoveTo(ShowStatus.CANCELLED));
        assertTrue(ShowStatus.OPEN.canMoveTo(ShowStatus.SOLD_OUT));
        assertTrue(ShowStatus.OPEN.canMoveTo(ShowStatus.CANCELLED));
        assertTrue(ShowStatus.OPEN.canMoveTo(ShowStatus.COMPLETED));
        assertTrue(ShowStatus.SOLD_OUT.canMoveTo(ShowStatus.COMPLETED));
    }

    @Test
    void show_rejectsInvalidMoves() {
        assertFalse(ShowStatus.SCHEDULED.canMoveTo(ShowStatus.SOLD_OUT));
        assertFalse(ShowStatus.SCHEDULED.canMoveTo(ShowStatus.COMPLETED));
        assertFalse(ShowStatus.OPEN.canMoveTo(ShowStatus.SCHEDULED));
        assertFalse(ShowStatus.CANCELLED.canMoveTo(ShowStatus.OPEN));
        assertFalse(ShowStatus.COMPLETED.canMoveTo(ShowStatus.OPEN));
    }
}
