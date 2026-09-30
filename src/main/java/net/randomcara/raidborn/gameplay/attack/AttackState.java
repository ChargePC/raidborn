package net.randomcara.raidborn.gameplay.attack;

public enum AttackState {
    ACTIVE,

    VICTORY,

    FAILED,

    ABANDONED;

    public boolean isOver() {
        return this != ACTIVE;
    }

    public boolean isDefeat() {
        return this == FAILED || this == ABANDONED;
    }
}
