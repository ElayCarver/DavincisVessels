package darkevilmac.archimedes.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import darkevilmac.archimedes.client.LanguageEntries;
import darkevilmac.archimedes.common.tileentity.TileEntityEngine;

public class GuiEngine extends GuiContainer {
    public static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("archimedesshipsplus", "textures/gui/engine.png");

    public GuiEngine(TileEntityEngine tileentity, EntityPlayer entityplayer) {
        super(new ContainerEngine(tileentity, entityplayer));
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mousex, int mousey) {
        int color = 0x404040;
        int row = 8;
        int col0 = 8;

        fontRendererObj.drawString(I18n.format( LanguageEntries.GUI_ENGINE_TITLE), col0, row, color);
        row += 5;

        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 96 + 2, color);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float var1, int var2, int var3) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        mc.renderEngine.bindTexture(BACKGROUND_TEXTURE);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        drawTexturedModalRect(x, y, 0, 0, xSize, ySize);
    }

}
