package net.randomcara.raidborn.core.util;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.randomcara.raidborn.Raidborn;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * Reads which entity type an {@link AvoidEntityGoal} runs away from.
 *
 * <p>{@code AvoidEntityGoal} keeps that type in a private field with no accessor, so reflection is
 * the only way to tell "flees from players" apart from "flees from wolves" before deciding to strip
 * the goal. Resolved fields are cached per goal class: the lookup happens on every entity join, and
 * scanning the field table each time would be wasted work.
 *
 * <p>Only fields declared directly on the goal's own class are considered, which is why a mod's
 * subclass of {@code AvoidEntityGoal} reports {@code null} — the inherited field is not visible
 * here. That is deliberate: it also keeps this from stripping Raidborn's own avoid goal.
 */
public final class AvoidGoals {
    private static final Map<Class<?>, List<Field>> TYPE_FIELDS = new ConcurrentHashMap<>();

    private AvoidGoals() {
    }

    /** The entity type the goal avoids, or {@code null} when it cannot be read. */
    @Nullable
    public static Class<?> avoidedType(AvoidEntityGoal<?> goal) {
        for (Field field : typeFields(goal.getClass())) {
            try {
                if (field.get(goal) instanceof Class<?> avoided) {
                    return avoided;
                }
            } catch (IllegalAccessException | RuntimeException e) {
                Raidborn.LOGGER.debug("Could not read avoid target from {}.{}",
                        goal.getClass().getName(), field.getName(), e);
            }
        }

        return null;
    }

    /** Drops every goal that makes the mob flee from players. */
    public static void removeAvoidPlayerGoals(Mob mob) {
        Iterator<WrappedGoal> goals = mob.goalSelector.getAvailableGoals().iterator();

        while (goals.hasNext()) {
            Goal goal = goals.next().getGoal();

            if (goal instanceof AvoidEntityGoal<?> avoidGoal && avoidedType(avoidGoal) == Player.class) {
                goals.remove();
            }
        }
    }

    private static List<Field> typeFields(Class<?> goalType) {
        return TYPE_FIELDS.computeIfAbsent(goalType, AvoidGoals::resolveTypeFields);
    }

    private static List<Field> resolveTypeFields(Class<?> goalType) {
        List<Field> fields = new ArrayList<>(1);

        for (Field field : goalType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!Class.class.isAssignableFrom(field.getType())) continue;

            try {
                field.setAccessible(true);
                fields.add(field);
            } catch (RuntimeException e) {
                // SecurityException or InaccessibleObjectException: the goal stays unreadable and
                // the caller leaves it installed, which is the safe direction.
                Raidborn.LOGGER.debug("Could not open {}.{} for reading",
                        goalType.getName(), field.getName(), e);
            }
        }

        return List.copyOf(fields);
    }
}
