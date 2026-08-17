package net.randomcara.raidborn.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.randomcara.bentoslib.api.curio.IActivatableCurioItem;
import net.randomcara.bentoslib.curio.CurioActivationHelper;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.config.RaidbornClientConfig;
import net.randomcara.raidborn.core.registry.ModItems;

public final class ArtifactHudOverlay {

    private ArtifactHudOverlay() {
    }

    private static final ResourceLocation ARTIFACT_SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/gui/sprites/hud/artifact_slot.png");

    private static final int SLOT_SIZE = 24;

    private static final int DEFAULT_X_OFFSET_FROM_CENTER = 98;
    private static final int DEFAULT_Y_OFFSET_FROM_BOTTOM = 23;

    private static final int OFFHAND_GAP = 1;
    private static final int OFFHAND_SLOT_WIDTH = 29;

    public static final IGuiOverlay OVERLAY = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();

        if (!RaidbornClientConfig.showArtifactHud()) {
            return;
        }

        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        if (mc.player.isSpectator()) {
            return;
        }

        ItemStack artifact = getEquippedArtifact(mc.player);
        if (artifact.isEmpty()) {
            return;
        }

        HudPosition position = getHudPosition(mc, screenWidth, screenHeight);

        int x = position.x();
        int y = position.y();

        guiGraphics.blit(ARTIFACT_SLOT_TEXTURE, x, y, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);

        int itemX = x + 4;
        int itemY = y + 4;

        guiGraphics.renderItem(artifact, itemX, itemY);
        guiGraphics.renderItemDecorations(mc.font, artifact, itemX, itemY);

        if (mc.player.getCooldowns().isOnCooldown(artifact.getItem())) {
            float percent = mc.player.getCooldowns().getCooldownPercent(artifact.getItem(), partialTick);
            int height = (int) (16.0F * percent);

            guiGraphics.fill(itemX, itemY + (16 - height), itemX + 16, itemY + 16, 0x80FFFFFF);
        }
    };

    private static HudPosition getHudPosition(Minecraft mc, int screenWidth, int screenHeight) {
        if (RaidbornClientConfig.useDefaultArtifactHudPosition()) {
            return getDefaultPosition(screenWidth, screenHeight);
        }

        return getBesideOffhandPosition(mc, screenWidth, screenHeight);
    }

    private static HudPosition getDefaultPosition(int screenWidth, int screenHeight) {
        int x = (screenWidth / 2) + DEFAULT_X_OFFSET_FROM_CENTER;
        int y = screenHeight - DEFAULT_Y_OFFSET_FROM_BOTTOM;

        return new HudPosition(x, y);
    }

    private static HudPosition getBesideOffhandPosition(Minecraft mc, int screenWidth, int screenHeight) {
        if (mc.player == null) {
            return getDefaultPosition(screenWidth, screenHeight);
        }

        int centerX = screenWidth / 2;
        int hotbarLeft = centerX - 91;
        int hotbarRight = centerX + 91;
        int y = screenHeight - DEFAULT_Y_OFFSET_FROM_BOTTOM;

        boolean offhandIsOnLeft = mc.player.getMainArm() == HumanoidArm.RIGHT;

        int x;

        if (offhandIsOnLeft) {
            x = hotbarLeft - OFFHAND_SLOT_WIDTH - SLOT_SIZE - OFFHAND_GAP;
        } else {
            x = hotbarRight + OFFHAND_SLOT_WIDTH + OFFHAND_GAP;
        }

        return new HudPosition(x, y);
    }

    private static ItemStack getEquippedArtifact(net.minecraft.world.entity.player.Player player) {
        ItemStack stack;

        stack = findEquipped(player, ModItems.ANYWHERE_PILLOW.get());
        if (!stack.isEmpty()) return stack;

        stack = findEquipped(player, ModItems.BIG_RED_BUTTON.get());
        if (!stack.isEmpty()) return stack;

        stack = findEquipped(player, ModItems.ARCANE_DICE.get());
        if (!stack.isEmpty()) return stack;

        stack = findEquipped(player, ModItems.TOTEM_OF_HEALING.get());
        if (!stack.isEmpty()) return stack;

        stack = findEquipped(player, ModItems.TOTEM_OF_PROTECTION.get());
        if (!stack.isEmpty()) return stack;

        stack = findEquipped(player, ModItems.TOTEM_OF_RESISTANCE.get());
        if (!stack.isEmpty()) return stack;

        stack = findEquipped(player, ModItems.VOODOO_VILLAGER_DOLL.get());
        if (!stack.isEmpty()) return stack;

        return ItemStack.EMPTY;
    }

    private static ItemStack findEquipped(net.minecraft.world.entity.player.Player player, Item item) {
        ItemStack stack = CurioActivationHelper.getEquippedStack(player, item);

        if (!(stack.getItem() instanceof IActivatableCurioItem)) {
            return ItemStack.EMPTY;
        }

        return stack;
    }

    private record HudPosition(int x, int y) {
    }
}