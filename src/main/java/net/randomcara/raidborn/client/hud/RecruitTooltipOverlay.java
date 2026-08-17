package net.randomcara.raidborn.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.config.RaidbornClientConfig;
import net.randomcara.raidborn.core.registry.ModEffects;

public final class RecruitTooltipOverlay {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/gui/recruit_tooltip/stats.png");

    private static final int TEXTURE_WIDTH = 47;
    private static final int TEXTURE_HEIGHT = 32;

    private static final int CROSSHAIR_GAP = 12;
    private static final int TOP_Y = 10;

    private static final int REQUEST_INTERVAL_TICKS = 5;
    private static final int DATA_TIMEOUT_TICKS = 10;

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private static final int HP_X = 27;
    private static final int HP_Y = 5;

    private static final int SLOTS_X = 27;
    private static final int SLOTS_Y = 19;

    private static int lookedEntityId = -1;
    private static int lastRequestedEntityId = -1;
    private static int requestCooldown;

    private static int dataEntityId = -1;
    private static int dataExpireTick;
    private static int slots;
    private static boolean validData;

    private RecruitTooltipOverlay() {
    }

    public static final IGuiOverlay OVERLAY = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();

        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) {
            return;
        }

        if (RaidbornClientConfig.recruitTooltipPosition() == RaidbornClientConfig.RecruitTooltipPosition.HIDDEN) {
            return;
        }

        if (!hasRecruitTooltipAccess(mc)) {
            return;
        }

        if (!isShiftDown(mc)) {
            return;
        }

        if (!validData || dataEntityId != lookedEntityId || mc.player.tickCount > dataExpireTick) {
            return;
        }

        Mob lookedMob = getLookedMob(mc);
        if (lookedMob == null || lookedMob.getId() != dataEntityId || !lookedMob.isAlive() || lookedMob.isRemoved()) {
            return;
        }

        int currentHp = Math.max(0, Mth.ceil(lookedMob.getHealth()));

        HudPosition position = getHudPosition(
                screenWidth,
                screenHeight,
                RaidbornClientConfig.recruitTooltipPosition()
        );

        render(guiGraphics, mc.font, position.x(), position.y(), currentHp);
    };

    public static void clientTick(Minecraft mc) {
        if (mc.player == null) {
            clear();
            return;
        }

        if (mc.screen != null
                || RaidbornClientConfig.recruitTooltipPosition() == RaidbornClientConfig.RecruitTooltipPosition.HIDDEN
                || !hasRecruitTooltipAccess(mc)
                || !isShiftDown(mc)) {
            clear();
            return;
        }

        Mob targetMob = getLookedMob(mc);
        int targetId = targetMob != null ? targetMob.getId() : -1;
        lookedEntityId = targetId;

        if (targetId < 0) {
            lastRequestedEntityId = -1;
            requestCooldown = 0;
            validData = false;
            dataEntityId = -1;
            return;
        }

        if (targetId != lastRequestedEntityId) {
            lastRequestedEntityId = targetId;
            requestCooldown = 0;
            validData = false;
        }

        if (requestCooldown > 0) {
            requestCooldown--;
            return;
        }

        Raidborn.CHANNEL.sendToServer(new Raidborn.RecruitTooltipRequestPacket(targetId));
        requestCooldown = REQUEST_INTERVAL_TICKS;
    }

    public static void handleTooltipData(int entityId, boolean valid, int hpValue, int slotValue, boolean isRecruited) {
        Minecraft mc = Minecraft.getInstance();
        int currentTick = mc.player != null ? mc.player.tickCount : 0;

        dataEntityId = entityId;
        validData = valid;
        slots = Math.max(0, slotValue);
        dataExpireTick = currentTick + DATA_TIMEOUT_TICKS;
    }

    public static void clear() {
        lookedEntityId = -1;
        lastRequestedEntityId = -1;
        requestCooldown = 0;

        dataEntityId = -1;
        dataExpireTick = 0;
        slots = 0;
        validData = false;
    }

    private static void render(GuiGraphics guiGraphics, Font font, int x, int y, int currentHp) {
        guiGraphics.blit(TEXTURE, x, y, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        String hpText = Integer.toString(currentHp);
        String slotsText = Integer.toString(slots);

        // trailing true = vanilla drop shadow
        guiGraphics.drawString(font, hpText, x + HP_X, y + HP_Y, TEXT_COLOR, true);
        guiGraphics.drawString(font, slotsText, x + SLOTS_X, y + SLOTS_Y, TEXT_COLOR, true);
    }

    private static Mob getLookedMob(Minecraft mc) {
        if (!(mc.hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        Entity entity = entityHitResult.getEntity();
        return entity instanceof Mob mob ? mob : null;
    }

    private static boolean hasRecruitTooltipAccess(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }

        return mc.player.hasEffect(ModEffects.ILLAGER_LOYALTY.get())
                || mc.player.hasEffect(ModEffects.ILLAGER_HONOR.get())
                || mc.player.hasEffect(ModEffects.HERO_OF_THE_RAID.get());
    }

    private static HudPosition getHudPosition(
            int screenWidth,
            int screenHeight,
            RaidbornClientConfig.RecruitTooltipPosition position
    ) {
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        return switch (position) {
            case LEFT -> new HudPosition(
                    centerX - CROSSHAIR_GAP - TEXTURE_WIDTH,
                    centerY - (TEXTURE_HEIGHT / 2)
            );

            case TOP -> new HudPosition(
                    centerX - (TEXTURE_WIDTH / 2),
                    TOP_Y
            );

            case RIGHT, HIDDEN -> new HudPosition(
                    centerX + CROSSHAIR_GAP,
                    centerY - (TEXTURE_HEIGHT / 2)
            );
        };
    }

    private static boolean isShiftDown(Minecraft mc) {
        return mc.options.keyShift.isDown();
    }

    private record HudPosition(int x, int y) {
    }
}