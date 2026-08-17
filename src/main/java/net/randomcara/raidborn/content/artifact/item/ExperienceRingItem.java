package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ExperienceRingItem extends Item implements ICurioItem {

    private static final String TAG_STORED_XP = "RaidbornStoredXp";
    private static final int MAX_STORED_LEVELS = 30;
    private static final int MAX_STORED_XP = getXpForLevel(MAX_STORED_LEVELS);

    private static final Set<UUID> RESTORING_XP = new HashSet<>();

    public static void clearServerState() {
        RESTORING_XP.clear();
    }

    public ExperienceRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return "ring".equals(slotContext.identifier());
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof ServerPlayer player) {
            restoreStoredExperience(player, stack);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            restoreStoredExperience(player, stack);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || getStoredExperience(stack) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int storedXp = getStoredExperience(stack);

        if (storedXp > 0) {
            tooltip.add(TooltipHelper.line("Stored Experience: " + storedXp + " XP", 0xFFA1FF99));
        }

        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Doubles experience gained while equipped", 0xFFA1FF99),
                TooltipHelper.line("Stores up to 30 levels when you die", 0xFFB7E45A),
                TooltipHelper.line("Pick up the ring after death to recover the stored XP", 0xFFB7E45A)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }

    private static Optional<ItemStack> findEquippedRing(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve().flatMap(handler -> {
            ICurioStacksHandler ringHandler = handler.getCurios().get("ring");

            if (ringHandler == null) {
                return Optional.empty();
            }

            for (int i = 0; i < ringHandler.getStacks().getSlots(); i++) {
                ItemStack stack = ringHandler.getStacks().getStackInSlot(i);

                if (stack.getItem() instanceof ExperienceRingItem) {
                    return Optional.of(stack);
                }
            }

            return Optional.empty();
        });
    }

    private static boolean hasEquippedRing(ServerPlayer player) {
        return findEquippedRing(player).isPresent();
    }

    private static int getStoredExperience(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null) {
            return 0;
        }

        return Math.max(0, tag.getInt(TAG_STORED_XP));
    }

    private static void setStoredExperience(ItemStack stack, int amount) {
        if (amount <= 0) {
            CompoundTag tag = stack.getTag();

            if (tag != null) {
                tag.remove(TAG_STORED_XP);
            }

            return;
        }

        stack.getOrCreateTag().putInt(TAG_STORED_XP, Math.min(amount, MAX_STORED_XP));
    }

    private static void restoreStoredExperience(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof ExperienceRingItem)) {
            return;
        }

        int storedXp = getStoredExperience(stack);

        if (storedXp <= 0) {
            return;
        }

        setStoredExperience(stack, 0);

        RESTORING_XP.add(player.getUUID());

        try {
            player.giveExperiencePoints(storedXp);
        } finally {
            RESTORING_XP.remove(player.getUUID());
        }
    }

    private static int getPlayerTotalExperience(Player player) {
        int levelXp = getXpForLevel(player.experienceLevel);
        int progressXp = Math.round(player.experienceProgress * player.getXpNeededForNextLevel());

        return Math.max(0, levelXp + progressXp);
    }

    private static int getXpForLevel(int level) {
        if (level <= 0) {
            return 0;
        }

        if (level <= 16) {
            return level * level + 6 * level;
        }

        if (level <= 31) {
            return (int) Math.floor(2.5D * level * level - 40.5D * level + 360.0D);
        }

        return (int) Math.floor(4.5D * level * level - 162.5D * level + 2220.0D);
    }

    @Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
    public static class Events {

        @SubscribeEvent
        public static void onPlayerDeath(LivingDeathEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }

            Optional<ItemStack> ringStack = findEquippedRing(player);

            if (ringStack.isEmpty()) {
                return;
            }

            int playerXp = getPlayerTotalExperience(player);
            int xpToStore = Math.min(playerXp, MAX_STORED_XP);

            if (xpToStore <= 0) {
                return;
            }

            ItemStack stack = ringStack.get();
            int alreadyStored = getStoredExperience(stack);

            setStoredExperience(stack, Math.max(alreadyStored, xpToStore));
        }

        @SubscribeEvent
        public static void onXpChange(PlayerXpEvent.XpChange event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }

            if (event.getAmount() <= 0) {
                return;
            }

            if (RESTORING_XP.contains(player.getUUID())) {
                return;
            }

            if (!hasEquippedRing(player)) {
                return;
            }

            event.setAmount(event.getAmount() * 2);
        }
    }
}