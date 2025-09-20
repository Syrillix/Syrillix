package com.syrillix.syrillixclient.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiSlider extends GuiButton {
    private float value = 0.5f; // От 0.0 до 1.0
    private float min, max;
    private String label;

    public GuiSlider(int id, int x, int y, int width, int height, String label, float min, float max, float initial) {
        super(id, x, y, width, height, "");
        this.label = label;
        this.min = min;
        this.max = max;
        this.value = (initial - min) / (max - min);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (visible) {
            drawRect(xPosition, yPosition + height / 2 - 1, xPosition + width, yPosition + height / 2 + 1, 0xFF808080); // Полоса
            int knobX = xPosition + (int)(value * (width - 8));
            drawRect(knobX, yPosition, knobX + 8, yPosition + height, 0xFFFFFFFF); // Кнопка
            mc.fontRendererObj.drawString(label + ": " + getActualValue(), xPosition, yPosition - 10, 0xFFFFFF);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            value = (float)(mouseX - (xPosition + 4)) / (float)(width - 8);
            value = Math.max(0, Math.min(1, value));
            return true;
        }
        return false;
    }

    public float getActualValue() {
        return min + value * (max - min);
    }

    public void setValue(float val) {
        value = (val - min) / (max - min);
    }
}