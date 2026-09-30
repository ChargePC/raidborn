package net.randomcara.raidborn.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class HeroOfTheRaidEffect extends MobEffect {
    public HeroOfTheRaidEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC9372C);
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return Collections.emptyList();
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }
}
