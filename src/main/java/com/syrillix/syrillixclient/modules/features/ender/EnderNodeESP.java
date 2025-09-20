package com.syrillix.syrillixclient.modules.features.ender;

import com.syrillix.syrillixclient.Main;
import com.syrillix.syrillixclient.gui.Category;
import com.syrillix.syrillixclient.gui.Settings.Setting;
import com.syrillix.syrillixclient.modules.Module;
import com.syrillix.syrillixclient.util.Utils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EnderNodeESP extends Module {

    private Setting.BooleanSetting showText;
    private Setting.SliderSetting renderDistance;
    private Setting.BooleanSetting showThroughWalls;
    private Setting.SliderSetting lineWidth;
    private Setting.SliderSetting scanFrequency;

    // Thread-safe list to store found ender nodes
    private final CopyOnWriteArrayList<BlockPos> enderNodes = new CopyOnWriteArrayList<>();

    // Executor service for background scanning
    private ScheduledExecutorService scanExecutor;

    // Color for rendering
    private final Color enderNodeColor = new Color(138, 43, 226, 255);

    public EnderNodeESP() {
        super("EnderNodeESP", Keyboard.KEY_NONE, Category.CategoryType.END);

        // Add settings
        this.showText = new Setting.BooleanSetting("Show Messages", true);
        this.renderDistance = new Setting.SliderSetting("Render Distance", 50, 10, 100, 1);
        this.showThroughWalls = new Setting.BooleanSetting("Show Through Walls", true);
        this.lineWidth = new Setting.SliderSetting("Line Width", 2.0f, 0.5f, 5.0f, 1);
        this.scanFrequency = new Setting.SliderSetting("Scan Frequency (s)", 2.0f, 0.5f, 10.0f, 0.5f);

        // Register settings
        addSetting(showText);
        addSetting(renderDistance);
        addSetting(showThroughWalls);
        addSetting(lineWidth);
        addSetting(scanFrequency);
    }

    @Override
    public void onEnable() {
        if (showText.getValue() && Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §a" + getName() + " §fenabled!"));
        }

        // Start the scanning thread
        startScanningThread();
    }

    @Override
    public void onDisable() {
        if (showText.getValue() && Utils.canUpdate()) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }

        // Stop the scanning thread
        stopScanningThread();

        // Clear the list of ender nodes
        enderNodes.clear();
    }

    private void startScanningThread() {
        // Stop any existing executor
        stopScanningThread();

        // Create a new scheduled executor
        scanExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("EnderNodeScanner");
            thread.setDaemon(true); // Make it a daemon thread so it doesn't prevent JVM shutdown
            return thread;
        });

        // Schedule the scanning task
        scanExecutor.scheduleAtFixedRate(
                this::scanForEnderNodes,
                0,
                (long)(scanFrequency.getValue() * 1000),
                TimeUnit.MILLISECONDS
        );
    }

    private void stopScanningThread() {
        if (scanExecutor != null) {
            scanExecutor.shutdownNow();
            scanExecutor = null;
        }
    }

    private void scanForEnderNodes() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || !Utils.canUpdate()) return;

            // Get player position
            BlockPos playerPos = mc.thePlayer.getPosition();
            int playerX = playerPos.getX();
            int playerY = playerPos.getY();
            int playerZ = playerPos.getZ();
            int distance = (int) renderDistance.getValue();

            // Create a new list to store found nodes
            List<BlockPos> newNodes = new ArrayList<>();

            // Scan for purple stained hardened clay blocks
            for (int x = playerX - distance; x <= playerX + distance; x++) {
                for (int y = playerY - distance; y <= playerY + distance; y++) {
                    for (int z = playerZ - distance; z <= playerZ + distance; z++) {
                        BlockPos pos = new BlockPos(x, y, z);

                        // Skip if the chunk isn't loaded
                        if (!mc.theWorld.isBlockLoaded(pos)) continue;

                        // Get the block and its metadata
                        Block block = mc.theWorld.getBlockState(pos).getBlock();
                        int metadata = block.getMetaFromState(mc.theWorld.getBlockState(pos));

                        // Check if it's stained hardened clay with purple color (metadata 10)
                        if (block == Blocks.stained_hardened_clay && metadata == 10) {
                            newNodes.add(pos);
                        }
                    }
                }
            }

            // Update the list of ender nodes
            enderNodes.clear();
            enderNodes.addAll(newNodes);
        } catch (Exception e) {
            // Log any exceptions
            System.err.println("Error in EnderNodeESP scanning thread: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!this.isEnabled() || !Utils.canUpdate() || enderNodes.isEmpty()) return;

        // Setup GL for rendering
        GlStateManager.pushMatrix();

        if (showThroughWalls.getValue()) {
            GlStateManager.disableDepth();
        }

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glLineWidth(lineWidth.getValue());

        // Get the partial ticks for smooth rendering
        float partialTicks = event.partialTicks;
        double d0 = Minecraft.getMinecraft().thePlayer.lastTickPosX + (Minecraft.getMinecraft().thePlayer.posX - Minecraft.getMinecraft().thePlayer.lastTickPosX) * partialTicks;
        double d1 = Minecraft.getMinecraft().thePlayer.lastTickPosY + (Minecraft.getMinecraft().thePlayer.posY - Minecraft.getMinecraft().thePlayer.lastTickPosY) * partialTicks;
        double d2 = Minecraft.getMinecraft().thePlayer.lastTickPosZ + (Minecraft.getMinecraft().thePlayer.posZ - Minecraft.getMinecraft().thePlayer.lastTickPosZ) * partialTicks;

        // Render all found ender nodes
        for (BlockPos pos : enderNodes) {
            drawBlockESP(pos, enderNodeColor, d0, d1, d2);
        }

        // Reset GL state
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GL11.glLineWidth(1.0f);
        GlStateManager.popMatrix();
    }

    private void drawBlockESP(BlockPos pos, Color color, double playerX, double playerY, double playerZ) {
        double x = pos.getX() - playerX;
        double y = pos.getY() - playerY;
        double z = pos.getZ() - playerZ;

        // Create a bounding box for the block
        AxisAlignedBB bb = new AxisAlignedBB(x, y, z, x + 1.0, y + 1.0, z + 1.0);

        // Draw the outline
        float red = color.getRed() / 255.0f;
        float green = color.getGreen() / 255.0f;
        float blue = color.getBlue() / 255.0f;
        float alpha = color.getAlpha() / 255.0f;

        // Draw the outline manually for better control
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        // Draw lines for each edge of the cube
        worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        // Bottom face
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();

        // Top face
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();

        // Connecting lines
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();
    }
}