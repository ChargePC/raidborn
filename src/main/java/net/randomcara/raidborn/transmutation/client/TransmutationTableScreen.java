package net.randomcara.raidborn.transmutation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.transmutation.menu.TransmutationTableMenu;

public class TransmutationTableScreen extends AbstractContainerScreen<TransmutationTableMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/gui/transmutation_table.png");
    private static final ResourceLocation BREW_PROGRESS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/gui/transmutation_table/brew_progress.png");
    private static final ResourceLocation BUBBLES_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/gui/transmutation_table/bubbles.png");
    private static final ResourceLocation FUEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/gui/transmutation_table/fuel_length.png");

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int FUEL_X = 60;
    private static final int FUEL_Y = 44;
    private static final int FUEL_WIDTH = 18;
    private static final int FUEL_HEIGHT = 4;

    private static final int BUBBLES_X = 63;
    private static final int BUBBLES_Y = 14;
    private static final int BUBBLES_WIDTH = 12;
    private static final int BUBBLES_HEIGHT = 29;

    private static final int BREW_PROGRESS_X = 97;
    private static final int BREW_PROGRESS_Y = 16;
    private static final int BREW_PROGRESS_WIDTH = 9;
    private static final int BREW_PROGRESS_HEIGHT = 28;

    public TransmutationTableScreen(TransmutationTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
        this.titleLabelY = 4;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleX = (this.imageWidth - this.font.width(this.title)) / 2;

        guiGraphics.drawString(this.font, this.title, titleX, this.titleLabelY, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.blit(GUI_TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        drawFuel(guiGraphics, x, y);
        drawBubbles(guiGraphics, x, y);
        drawProgress(guiGraphics, x, y);
    }

    private void drawFuel(GuiGraphics guiGraphics, int x, int y) {
        if (!this.menu.hasFuel()) {
            return;
        }

        guiGraphics.blit(FUEL_TEXTURE, x + FUEL_X, y + FUEL_Y, 0, 0, this.menu.getFuelWidth(), FUEL_HEIGHT, FUEL_WIDTH, FUEL_HEIGHT);
    }

    private void drawBubbles(GuiGraphics guiGraphics, int x, int y) {
        if (!this.menu.isCrafting()) {
            return;
        }

        int bubbleHeight = this.menu.getBubbleHeight();
        if (bubbleHeight <= 0) {
            return;
        }

        guiGraphics.blit(
                BUBBLES_TEXTURE,
                x + BUBBLES_X,
                y + BUBBLES_Y + (BUBBLES_HEIGHT - bubbleHeight),
                0,
                BUBBLES_HEIGHT - bubbleHeight,
                BUBBLES_WIDTH,
                bubbleHeight,
                BUBBLES_WIDTH,
                BUBBLES_HEIGHT
        );
    }

    private void drawProgress(GuiGraphics guiGraphics, int x, int y) {
        if (!this.menu.isCrafting()) {
            return;
        }

        int progressHeight = this.menu.getScaledProgress();
        if (progressHeight > 0) {
            guiGraphics.blit(BREW_PROGRESS_TEXTURE, x + BREW_PROGRESS_X, y + BREW_PROGRESS_Y, 0, 0, BREW_PROGRESS_WIDTH, progressHeight, BREW_PROGRESS_WIDTH, BREW_PROGRESS_HEIGHT);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
