package com.syrillix.syrillixclient.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiCheckbox extends GuiButton {
    private boolean checked = false;
    private String label;

    public GuiCheckbox(int id, int x, int y, int width, int height, String label, boolean initial) {
        super(id, x, y, width, height, "");
        this.label = label;
        this.checked = initial;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (visible) {
            drawRect(xPosition, yPosition, xPosition + height, yPosition + height, 0xFF000000); // Квадрат
            if (checked) {
                drawRect(xPosition + 2, yPosition + 2, xPosition + height - 2, yPosition + height - 2, 0xFFFFFFFF); // Галка
            }
            mc.fontRendererObj.drawString(label, xPosition + height + 5, yPosition + (height - 8) / 2, 0xFFFFFF);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            checked = !checked;
            return true;
        }
        return false;
    }

    public boolean isChecked() {
        return checked;
    }
}