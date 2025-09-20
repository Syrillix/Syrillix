package com.syrillix.syrillixclient.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import java.util.List;

public class GuiSelection extends GuiButton {
    private List<String> options;
    private int selected = 0;
    private String label;

    public GuiSelection(int id, int x, int y, int width, int height, String label, List<String> options, int initial) {
        super(id, x, y, width, height, "");
        this.label = label;
        this.options = options;
        this.selected = initial;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (visible) {
            mc.fontRendererObj.drawString(label + ": " + options.get(selected), xPosition, yPosition, 0xFFFFFF);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            selected = (selected + 1) % options.size();
            return true;
        }
        return false;
    }

    public String getSelected() {
        return options.get(selected);
    }
}