package net.randomcara.raidborn.core.util;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.randomcara.raidborn.Raidborn;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AvoidGoals {
    private static final Map<Class<?>, List<Field>> TYPE_FIELDS = new ConcurrentHashMap<>();

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
                Raidborn.LOGGER.debug("Could not open {}.{} for reading",
                        goalType.getName(), field.getName(), e);
            }
        }

        return List.copyOf(fields);
    }
}
