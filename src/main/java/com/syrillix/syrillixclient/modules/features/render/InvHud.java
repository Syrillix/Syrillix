package com.syrillix.syrillixclient.modules.features.render;

import com.syrillix.syrillixclient.Main;
import com.syrillix.syrillixclient.gui.Category;
import com.syrillix.syrillixclient.gui.Settings.Setting;
import com.syrillix.syrillixclient.modules.Module;
import com.syrillix.syrillixclient.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;

public class InvHud extends Module {

    private Setting.BooleanSetting showText;
    private Setting.BooleanSetting background;
    private Setting.SliderSetting backgroundOpacity;
    private Setting.SliderSetting scale;

    private int posX = 5;
    private int posY = 5;
    private boolean isDragging = false;
    private int dragX = 0;
    private int dragY = 0;

    // Constants for inventory layout
    private static final int SLOT_SIZE = 16;
    private static final int INVENTORY_WIDTH = 9 * SLOT_SIZE;
    private static final int INVENTORY_HEIGHT = 3 * SLOT_SIZE + SLOT_SIZE; // 3 rows + hotbar

    public InvHud() {
        super("InventoryHUD", Keyboard.KEY_NONE, Category.CategoryType.RENDER);

        // Add settings
        this.showText = new Setting.BooleanSetting("Show Messages", true);
        this.background = new Setting.BooleanSetting("Show Background", true);
        this.backgroundOpacity = new Setting.SliderSetting("Background Opacity", 0.5f, 0.0f, 1.0f, 0.05f);
        this.scale = new Setting.SliderSetting("Scale", 1.0f, 0.5f, 2.0f, 0.1f);

        // Register settings
        addSetting(showText);
        addSetting(background);
        addSetting(backgroundOpacity);
        addSetting(scale);
    }

    @Override
    public void onEnable() {
        if (showText.getValue() && Utils.canUpdate()) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §a" + getName() + " §fenabled!"));
        }
    }

    @Override
    public void onDisable() {
        if (showText.getValue() && Utils.canUpdate()) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        if (event.type == RenderGameOverlayEvent.ElementType.HOTBAR) {
            // Handle dragging
            handleDragging();

            // Render the inventory HUD
            renderInventoryHUD();
        }
    }

    private void handleDragging() {
        ScaledResolution scaledResolution = new ScaledResolution(mc);

        // Only allow dragging when chat is open
        boolean chatOpen = mc.currentScreen instanceof GuiChat;

        if (chatOpen) {
            int mouseX = Mouse.getX() * scaledResolution.getScaledWidth() / mc.displayWidth;
            int mouseY = scaledResolution.getScaledHeight() - Mouse.getY() * scaledResolution.getScaledHeight() / mc.displayHeight - 1;

            float scaleFactor = scale.getValue();
            int scaledWidth = (int)(INVENTORY_WIDTH * scaleFactor);
            int scaledHeight = (int)(INVENTORY_HEIGHT * scaleFactor);

            // Check if mouse is over the HUD
            boolean mouseOver = mouseX >= posX && mouseX <= posX + scaledWidth &&
                    mouseY >= posY && mouseY <= posY + scaledHeight;

            // Handle mouse click
            if (Mouse.isButtonDown(0)) {
                if (!isDragging && mouseOver) {
                    isDragging = true;
                    dragX = mouseX - posX;
                    dragY = mouseY - posY;
                } else if (isDragging) {
                    posX = mouseX - dragX;
                    posY = mouseY - dragY;

                    // Ensure the HUD stays within screen bounds
                    posX = Math.max(0, Math.min(posX, scaledResolution.getScaledWidth() - scaledWidth));
                    posY = Math.max(0, Math.min(posY, scaledResolution.getScaledHeight() - scaledHeight));
                }
            } else {
                isDragging = false;
            }
        }
    }

    private void renderInventoryHUD() {
        ScaledResolution scaledResolution = new ScaledResolution(mc);

        float scaleFactor = scale.getValue();
        int scaledWidth = (int)(INVENTORY_WIDTH * scaleFactor);
        int scaledHeight = (int)(INVENTORY_HEIGHT * scaleFactor);

        // Draw background if enabled
        if (background.getValue()) {
            Color bgColor = new Color(0, 0, 0, 40);
            int alpha = (int)(backgroundOpacity.getValue() * 255);
            drawRect(posX, posY, posX + scaledWidth, posY + scaledHeight,
                    new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), alpha).getRGB());
        }

        // Start rendering items
        GlStateManager.pushMatrix();
        GlStateManager.translate(posX, posY, 0);
        GlStateManager.scale(scaleFactor, scaleFactor, 1.0f);

        // Draw grid lines with the specified color
        int gridColor = new Color(0, 120, 215, 255).getRGB();

        // Draw horizontal grid lines
        for (int row = 0; row <= 4; row++) {
            int y = row * SLOT_SIZE;
            for (int x = 0; x < INVENTORY_WIDTH; x++) {
                drawRect(x, y, x + 1, y + 1, gridColor);
            }
        }

        // Draw vertical grid lines
        for (int col = 0; col <= 9; col++) {
            int x = col * SLOT_SIZE;
            for (int y = 0; y < INVENTORY_HEIGHT; y++) {
                drawRect(x, y, x + 1, y + 1, gridColor);
            }
        }

        // Enable item rendering
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();

        // Render hotbar items (bottom row)
        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack != null) {
                int x = i * SLOT_SIZE;
                int y = 3 * SLOT_SIZE; // Position at the bottom row

                mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, x, y);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, itemStack, x, y, null);
            }
        }

        // Render inventory items (top 3 rows)
        for (int i = 9; i < 36; i++) {
            ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack != null) {
                int slot = i - 9;
                int row = slot / 9;
                int col = slot % 9;

                int x = col * SLOT_SIZE;
                int y = row * SLOT_SIZE;

                mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, x, y);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, itemStack, x, y, null);
            }
        }

        // Disable item rendering
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();
    }

    private void drawHorizontalLine(int startX, int endX, int y, int color) {
        if (endX < startX) {
            int temp = startX;
            startX = endX;
            endX = temp;
        }

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        float alpha = (float)(color >> 24 & 255) / 255.0F;
        float red = (float)(color >> 16 & 255) / 255.0F;
        float green = (float)(color >> 8 & 255) / 255.0F;
        float blue = (float)(color & 255) / 255.0F;

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        worldrenderer.pos(startX, y, 0.0D).color(red, green, blue, alpha).endVertex();
        worldrenderer.pos(endX, y, 0.0D).color(red, green, blue, alpha).endVertex();
        worldrenderer.pos(endX, y + 1, 0.0D).color(red, green, blue, alpha).endVertex();
        worldrenderer.pos(startX, y + 1, 0.0D).color(red, green, blue, alpha).endVertex();
        tessellator.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    private void drawVerticalLine(int x, int startY, int endY, int color) {
        if (endY < startY) {
            int temp = startY;
            startY = endY;
            endY = temp;
        }

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        float alpha = (float)(color >> 24 & 255) / 255.0F;
        float red = (float)(color >> 16 & 255) / 255.0F;
        float green = (float)(color >> 8 & 255) / 255.0F;
        float blue = (float)(color & 255) / 255.0F;

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        worldrenderer.pos(x, startY, 0.0D).color(red, green, blue, alpha).endVertex();
        worldrenderer.pos(x + 1, startY, 0.0D).color(red, green, blue, alpha).endVertex();
        worldrenderer.pos(x + 1, endY, 0.0D).color(red, green, blue, alpha).endVertex();
        worldrenderer.pos(x, endY, 0.0D).color(red, green, blue, alpha).endVertex();
        tessellator.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }


    private void drawRect(int left, int top, int right, int bottom, int color) {
        if (left < right) {
            int temp = left;
            left = right;
            right = temp;
        }

        if (top < bottom) {
            int temp = top;
            top = bottom;
            bottom = temp;
        }

        float alpha = (float)(color >> 24 & 255) / 255.0F;
        float red = (float)(color >> 16 & 255) / 255.0F;
        float green = (float)(color >> 8 & 255) / 255.0F;
        float blue = (float)(color & 255) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        worldrenderer.pos(left, bottom, 0.0D).color(red, green, blue, alpha).endVertex();
        worldrenderer.pos(right, bottom, 0.0D).color(red, green, blue, alpha).endVertex();
        worldrenderer.pos(right, top, 0.0D).color(red, green, blue, alpha).endVertex();
        worldrenderer.pos(left, top, 0.0D).color(red, green, blue, alpha).endVertex();
        tessellator.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}