package net.randomcara.raidborn.content.artifact.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OminousDaggerItem extends SwordItem {

    public static final String VILLAGER_KILLS_TAG = "VillagerKills";

    private static final float DAMAGE_BONUS_PER_KILL = 0.025F;
    private static final float MAX_DAMAGE_BONUS = 1.0F;
    private static final int MAX_BONUS_KILLS = 40;

    private static final UUID OMINOUS_DAGGER_REACH_UUID = UUID.fromString("7b1b8c5d-59c3-4f5f-b4c4-3de7dc0f4c11");

    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public OminousDaggerItem(Tier tier, Properties properties) {
        super(tier, 4, -1.6F, properties);

        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();

        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                BASE_ATTACK_DAMAGE_UUID,
                "Weapon modifier",
                7.0D,
                AttributeModifier.Operation.ADDITION
        ));

        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                BASE_ATTACK_SPEED_UUID,
                "Weapon modifier",
                -1.6D,
                AttributeModifier.Operation.ADDITION
        ));

        builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                OMINOUS_DAGGER_REACH_UUID,
                "Ominous Dagger reach modifier",
                -0.75D,
                AttributeModifier.Operation.ADDITION
        ));

        this.defaultModifiers = builder.build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.defaultModifiers : super.getDefaultAttributeModifiers(slot);
    }

    public static int getVillagerKills(ItemStack stack) {
        return stack.getOrCreateTag().getInt(VILLAGER_KILLS_TAG);
    }

    public static void addVillagerKill(ItemStack stack) {
        int kills = getVillagerKills(stack);
        stack.getOrCreateTag().putInt(VILLAGER_KILLS_TAG, kills + 1);
    }

    public static float getDamageBonusPercent(ItemStack stack) {
        float bonus = getVillagerKills(stack) * DAMAGE_BONUS_PER_KILL;
        return Math.min(bonus, MAX_DAMAGE_BONUS);
    }

    public static float getDamageMultiplier(ItemStack stack) {
        return 1.0F + getDamageBonusPercent(stack);
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment == Enchantments.UNBREAKING || enchantment == Enchantments.MENDING) {
            return false;
        }
        return super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(book);

        if (enchantments.containsKey(Enchantments.UNBREAKING) || enchantments.containsKey(Enchantments.MENDING)) {
            return false;
        }

        return super.isBookEnchantable(stack, book);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int villagerKills = getVillagerKills(stack);
        int progressKills = Math.min(villagerKills, MAX_BONUS_KILLS);
        float bonusPercent = getDamageBonusPercent(stack) * 100.0F;

        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Fast dagger, but with shorter reach", 0xAAAAAA),
                TooltipHelper.line("+2.5% damage for each villager slain", 0xAA3333),
                TooltipHelper.line(String.format("Damage bonus: +%.1f%% / 100%%", bonusPercent), 0xFF5555),
                TooltipHelper.line("Progress: " + progressKills + " / " + MAX_BONUS_KILLS, 0x8B0000),
                TooltipHelper.line("Cannot get Unbreaking or Mending", 0x777777)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
    public static class Events {

        @SubscribeEvent
        public static void onLivingHurt(LivingHurtEvent event) {
            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                ItemStack weapon = attacker.getMainHandItem();

                if (weapon.getItem() instanceof OminousDaggerItem) {
                    float multiplier = OminousDaggerItem.getDamageMultiplier(weapon);
                    event.setAmount(event.getAmount() * multiplier);
                }
            }
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            if (!(event.getEntity() instanceof Villager)) return;

            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                ItemStack weapon = attacker.getMainHandItem();

                if (weapon.getItem() instanceof OminousDaggerItem) {
                    OminousDaggerItem.addVillagerKill(weapon);
                }
            }
        }

        public static boolean isBannerCaptain(AbstractIllager illager) {
            return illager.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BannerItem;
        }
    }
}
