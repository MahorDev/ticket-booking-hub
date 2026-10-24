package com.tickethub.event.enums;

public enum EventStatus {
    DRAFT, PUBLISHED, CANCELLED;

    /** Allowed lifecycle: DRAFT -> PUBLISHED -> CANCELLED. */
    public boolean canMoveTo(EventStatus next) {
        return switch (this) {
            case DRAFT -> next == PUBLISHED;
            case PUBLISHED -> next == CANCELLED;
            case CANCELLED -> false;
        };
    }
}
