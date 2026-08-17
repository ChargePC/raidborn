package net.randomcara.raidborn.gameplay.recruit;

import java.util.Locale;

public enum SquadOrder {
    FOLLOW("follow"),
    ATTACK("attack"),
    HOLD("hold");

    private final String id;

    SquadOrder(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public static SquadOrder fromString(String value) {
        if (value == null || value.isBlank()) return FOLLOW;

        String normalized = value.toLowerCase(Locale.ROOT);
        for (SquadOrder order : values()) {
            if (order.id.equals(normalized)) {
                return order;
            }
        }

        return FOLLOW;
    }

    public SquadOrder nextSelectable() {
        return switch (this) {
            case FOLLOW -> ATTACK;
            case ATTACK -> HOLD;
            case HOLD -> FOLLOW;
        };
    }

    public boolean isSelectableInCycle() {
        return true;
    }
}
