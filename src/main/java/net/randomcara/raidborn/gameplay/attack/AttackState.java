package net.randomcara.raidborn.gameplay.attack;

/**
 * Where an Attack is in its lifecycle.
 *
 * <p>An Attack runs, then finishes exactly one way. Keeping that as a single field is what stops
 * combinations like "won and abandoned" from existing at all.
 */
public enum AttackState {
    ACTIVE,

    /** Every villager is down. */
    VICTORY,

    /** Ran out of time, or the owner died, or the village unloaded. */
    FAILED,

    /** The owner left the area for too long. Counts as a defeat, with its own boss bar text. */
    ABANDONED;

    public boolean isOver() {
        return this != ACTIVE;
    }

    public boolean isDefeat() {
        return this == FAILED || this == ABANDONED;
    }
}
