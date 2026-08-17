package net.randomcara.raidborn.core.registry;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.gameplay.loot.VillagerSoulLootModifier;

public class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Raidborn.MOD_ID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> VILLAGER_SOUL =
            LOOT_MODIFIER_SERIALIZERS.register("villager_soul", () -> VillagerSoulLootModifier.CODEC);
}
