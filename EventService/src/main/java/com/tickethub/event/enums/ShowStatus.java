package com.tickethub.event.enums;

public enum ShowStatus {
    SCHEDULED, OPEN, SOLD_OUT, CANCELLED, COMPLETED;

    /** Allowed lifecycle: SCHEDULED -> OPEN -> SOLD_OUT / CANCELLED / COMPLETED. */
    public boolean canMoveTo(ShowStatus next) {
        return switch (this) {
            case SCHEDULED -> next == OPEN || next == CANCELLED;
            case OPEN -> next == SOLD_OUT || next == CANCELLED || next == COMPLETED;
            case SOLD_OUT -> next == CANCELLED || next == COMPLETED;
            case CANCELLED, COMPLETED -> false;
        };
    }
}
