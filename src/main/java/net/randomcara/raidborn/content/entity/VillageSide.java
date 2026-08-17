package net.randomcara.raidborn.content.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.randomcara.raidborn.core.registry.ModTags;

import javax.annotation.Nullable;

/** Iron Gollet and Iron Juggernaut both extend {@link IronGolem}, so the golem test covers them. */
public final class VillageSide {
    private VillageSide() {
    }

    public static boolean isDefender(@Nullable Entity entity) {
        return entity instanceof AbstractVillager
                || entity instanceof IronGolem
                || entity instanceof SnowGolem;
    }

    public static boolean isAttackingVillage(@Nullable Entity entity) {
        return entity instanceof Mob mob && isDefender(mob.getTarget());
    }

    public static boolean isIllagerSide(@Nullable Entity entity) {
        return entity instanceof AbstractIllager
                || entity instanceof Ravager
                || entity instanceof Witch
                || entity instanceof Vex;
    }

    public static boolean isIllagerThreat(@Nullable Entity entity) {
        return entity instanceof LivingEntity living
                && living.isAlive()
                && !isDefender(living)
                && living.getType().is(ModTags.EntityTypes.ILLAGER_THREATS);
    }
}
