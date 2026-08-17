package net.randomcara.raidborn.content.entity.beast.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.beast.Beast;
import net.randomcara.raidborn.content.entity.beast.menu.BeastInventoryMenu;

public class BeastInventoryScreen extends AbstractContainerScreen<BeastInventoryMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    Raidborn.MOD_ID,
                    "textures/gui/beast_inventory.png"
            );

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;

    private static final int LABEL_COLOR = 0x404040;

    /** Black area of the texture, 52x52 at 26/18. The scissor keeps the Beast inside it. */
    private static final int BEAST_AREA_X = 26;
    private static final int BEAST_AREA_Y = 18;
    private static final int BEAST_AREA_WIDTH = 52;
    private static final int BEAST_AREA_HEIGHT = 52;

    private static final int BEAST_RENDER_X = 52;
    private static final int BEAST_RENDER_Y = 62;
    private static final int BEAST_RENDER_SCALE = 13;
    private static final int BEAST_LOOK_Y = 43;

    public BeastInventoryScreen(
            BeastInventoryMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);

        this.imageWidth = 176;
        this.imageHeight = 166;

        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void renderLabels(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY
    ) {
        guiGraphics.drawString(
                this.font,
                this.title,
                TITLE_X,
                TITLE_Y,
                LABEL_COLOR,
                false
        );

        guiGraphics.drawString(
                this.font,
                this.playerInventoryTitle,
                this.inventoryLabelX,
                this.inventoryLabelY,
                LABEL_COLOR,
                false
        );
    }

    @Override
    protected void renderBg(
            GuiGraphics guiGraphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
        guiGraphics.blit(
                GUI_TEXTURE,
                this.leftPos,
                this.topPos,
                0,
                0,
                this.imageWidth,
                this.imageHeight,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );

        renderBeast(guiGraphics, mouseX, mouseY);
    }

    private void renderBeast(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY
    ) {
        Beast beast = this.menu.getBeast();

        if (beast == null || !beast.isAlive()) {
            return;
        }

        int areaLeft = this.leftPos + BEAST_AREA_X;
        int areaTop = this.topPos + BEAST_AREA_Y;
        int areaRight = areaLeft + BEAST_AREA_WIDTH;
        int areaBottom = areaTop + BEAST_AREA_HEIGHT;

        int renderX = this.leftPos + BEAST_RENDER_X;
        int renderY = this.topPos + BEAST_RENDER_Y;

        float mouseOffsetX = (float) renderX - mouseX;
        float mouseOffsetY = (float) (this.topPos + BEAST_LOOK_Y) - mouseY;

        guiGraphics.enableScissor(
                areaLeft,
                areaTop,
                areaRight,
                areaBottom
        );

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                renderX,
                renderY,
                BEAST_RENDER_SCALE,
                mouseOffsetX,
                mouseOffsetY,
                beast
        );

        guiGraphics.disableScissor();
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
