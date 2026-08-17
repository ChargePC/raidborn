package net.randomcara.raidborn.gameplay.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.randomcara.bentoslib.gameplay.loot.ChanceDropLootModifier;
import net.randomcara.raidborn.content.artifact.item.OminousDaggerItem;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModItems;

/** Villager Soul drop, at double chance for kills with the Ominous Dagger. */
public class VillagerSoulLootModifier extends ChanceDropLootModifier {
    public static final Codec<VillagerSoulLootModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance).apply(instance, VillagerSoulLootModifier::new)
    );

    public VillagerSoulLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected boolean matches(Entity entity) {
        return entity instanceof Villager;
    }

    @Override
    protected ItemStack createDrop(LootContext context) {
        return new ItemStack(ModItems.VILLAGER_SOUL.get());
    }

    @Override
    protected float getDropChance(LootContext context) {
        float baseChance = (float) RaidbornServerConfig.getVillagerSoulDropChance();

        DamageSource source = context.getParamOrNull(LootContextParams.DAMAGE_SOURCE);
        if (source == null || !(source.getEntity() instanceof LivingEntity attacker)) {
            return baseChance;
        }

        if (attacker.getMainHandItem().getItem() instanceof OminousDaggerItem) {
            return Math.min(1.0F, baseChance * 2.0F);
        }

        return baseChance;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
