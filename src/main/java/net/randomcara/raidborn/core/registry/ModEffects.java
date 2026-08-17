package net.randomcara.raidborn.core.registry;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.effect.HeroOfTheRaidEffect;
import net.randomcara.raidborn.content.effect.IllagerHonorEffect;
import net.randomcara.raidborn.content.effect.IllagerLoyaltyEffect;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Raidborn.MOD_ID);

    public static final RegistryObject<MobEffect> ILLAGER_HONOR =
            EFFECTS.register("illager_honor", IllagerHonorEffect::new);

    public static final RegistryObject<MobEffect> ILLAGER_LOYALTY =
            EFFECTS.register("illager_loyalty", IllagerLoyaltyEffect::new);

    public static final RegistryObject<MobEffect> HERO_OF_THE_RAID =
            EFFECTS.register("hero_of_the_raid", HeroOfTheRaidEffect::new);

    /** The three effects are mutually exclusive; any of them marks an alliance with the illagers. */
    public static boolean hasAllianceEffect(LivingEntity entity) {
        return entity.hasEffect(ILLAGER_LOYALTY.get())
                || entity.hasEffect(ILLAGER_HONOR.get())
                || entity.hasEffect(HERO_OF_THE_RAID.get());
    }
}
