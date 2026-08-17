package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class AttackCelebration {
    private AttackCelebration() {
    }

    static void launchFirework(ServerLevel level, Entity source, DyeColor primary, DyeColor secondary, DyeColor fade) {
        level.addFreshEntity(new FireworkRocketEntity(
                level,
                source.getX(),
                source.getY() + source.getBbHeight() + 0.15D,
                source.getZ(),
                createFirework(primary, secondary, fade)
        ));
    }

    private static ItemStack createFirework(DyeColor primary, DyeColor secondary, DyeColor fade) {
        CompoundTag explosion = new CompoundTag();
        explosion.putByte("Type", (byte) 0);
        explosion.putBoolean("Flicker", true);
        explosion.putBoolean("Trail", true);
        explosion.putIntArray("Colors", new int[]{primary.getFireworkColor(), secondary.getFireworkColor()});
        explosion.putIntArray("FadeColors", new int[]{fade.getFireworkColor()});

        ListTag explosions = new ListTag();
        explosions.add(explosion);

        CompoundTag fireworks = new CompoundTag();
        fireworks.putByte("Flight", (byte) 1);
        fireworks.put("Explosions", explosions);

        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        stack.getOrCreateTag().put("Fireworks", fireworks);
        return stack;
    }
}
