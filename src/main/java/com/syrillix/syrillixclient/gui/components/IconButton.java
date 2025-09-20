package com.syrillix.syrillixclient.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

public class IconButton extends GuiButton {
    private ResourceLocation iconTexture;

    public IconButton(int id, int x, int y, int width, int height, ResourceLocation icon) {
        super(id, x, y, width, height, "");
        this.iconTexture = icon;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (visible) {
            mc.getTextureManager().bindTexture(iconTexture);
            drawTexturedModalRect(xPosition, yPosition, 0, 0, width, height); // Рисуем иконку
            if (isMouseOver(mouseX, mouseY)) {
                drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0x80000000); // Hover эффект
            }
        }
    }

    private boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
    }
}