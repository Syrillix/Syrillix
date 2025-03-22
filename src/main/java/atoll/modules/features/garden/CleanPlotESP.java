package atoll.modules.features.garden;

import atoll.gui.Category;
import atoll.gui.setting.Setting;
import atoll.modules.Module;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
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

public class CleanPlotESP extends Module {

    private Setting.BooleanSetting showText;
    private Setting.SliderSetting renderDistance;
    private Setting.BooleanSetting showThroughWalls;
    private Setting.SliderSetting lineWidth;
    private Setting.SliderSetting scanFrequency;

    private Setting.BooleanSetting highlightGrass;
    private Setting.BooleanSetting highlightLeaves;
    private Setting.BooleanSetting highlightFlowers;
    private Setting.BooleanSetting highlightOak;

    // Thread-safe list to store found blocks
    private final CopyOnWriteArrayList<BlockPosWithColor> gardenBlocks = new CopyOnWriteArrayList<>();

    // Executor service for background scanning
    private ScheduledExecutorService scanExecutor;

    // Colors for rendering different block types
    private final Color grassColor = new Color(0, 255, 0, 255);       // Green
    private final Color leavesColor = new Color(0, 128, 0, 255);      // Dark Green
    private final Color flowersColor = new Color(255, 0, 255, 255);   // Magenta
    private final Color oakColor = new Color(139, 69, 19, 255);       // Brown

    // Class to store block position with color
    private static class BlockPosWithColor {
        public final BlockPos pos;
        public final Color color;

        public BlockPosWithColor(BlockPos pos, Color color) {
            this.pos = pos;
            this.color = color;
        }
    }

    public CleanPlotESP() {
        super("Garden clean plot ESP", Keyboard.KEY_NONE, Category.CategoryType.GARDEN);

        // Add settings
        this.showText = new Setting.BooleanSetting("Show Messages", true);
        this.renderDistance = new Setting.SliderSetting("Render Distance", 50, 10, 100, 1);
        this.showThroughWalls = new Setting.BooleanSetting("Show Through Walls", true);
        this.lineWidth = new Setting.SliderSetting("Line Width", 2.0f, 0.5f, 5.0f, 1);
        this.scanFrequency = new Setting.SliderSetting("Scan Frequency (s)", 2.0f, 0.5f, 10.0f, 0.5f);

        this.highlightGrass = new Setting.BooleanSetting("Highlight Grass", true);
        this.highlightLeaves = new Setting.BooleanSetting("Highlight Leaves", true);
        this.highlightFlowers = new Setting.BooleanSetting("Highlight Flowers", true);
        this.highlightOak = new Setting.BooleanSetting("Highlight Oak", true);

        // Register settings
        addSetting(showText);
        addSetting(renderDistance);
        addSetting(showThroughWalls);
        addSetting(lineWidth);
        addSetting(scanFrequency);
        addSetting(highlightGrass);
        addSetting(highlightLeaves);
        addSetting(highlightFlowers);
        addSetting(highlightOak);
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
        if (showText.getValue() && Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }

        // Stop the scanning thread
        stopScanningThread();

        // Clear the list of garden blocks
        gardenBlocks.clear();
    }

    private void startScanningThread() {
        // Stop any existing executor
        stopScanningThread();

        // Create a new scheduled executor
        scanExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("GardenBlockScanner");
            thread.setDaemon(true); // Make it a daemon thread so it doesn't prevent JVM shutdown
            return thread;
        });

        // Schedule the scanning task
        scanExecutor.scheduleAtFixedRate(
                this::scanForGardenBlocks,
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

    private void scanForGardenBlocks() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;

            // Get player position
            BlockPos playerPos = mc.thePlayer.getPosition();
            int playerX = playerPos.getX();
            int playerY = playerPos.getY();
            int playerZ = playerPos.getZ();
            int distance = (int) renderDistance.getValue();

            // Create a new list to store found blocks
            List<BlockPosWithColor> newBlocks = new ArrayList<>();

            // Scan for garden blocks
            for (int x = playerX - distance; x <= playerX + distance; x++) {
                for (int y = playerY - distance; y <= playerY + distance; y++) {
                    for (int z = playerZ - distance; z <= playerZ + distance; z++) {
                        BlockPos pos = new BlockPos(x, y, z);

                        // Skip if the chunk isn't loaded
                        if (!mc.theWorld.isBlockLoaded(pos)) continue;

                        // Get the block
                        Block block = mc.theWorld.getBlockState(pos).getBlock();

                        // Check for grass blocks
                        if (highlightGrass.getValue() &&
                                (block == Blocks.tallgrass ||
                                        (block == Blocks.double_plant && isGrassVariant(mc, pos)))) {
                            newBlocks.add(new BlockPosWithColor(pos, grassColor));
                        }

                        // Check for leaves
                        else if (highlightLeaves.getValue() &&
                                (block == Blocks.leaves || block == Blocks.leaves2)) {
                            newBlocks.add(new BlockPosWithColor(pos, leavesColor));
                        }

                        // Check for flowers
                        else if (highlightFlowers.getValue() &&
                                (block == Blocks.red_flower || block == Blocks.yellow_flower ||
                                        block == Blocks.double_plant && isFlowerVariant(mc, pos))) {
                            newBlocks.add(new BlockPosWithColor(pos, flowersColor));
                        }

                        // Check for oak blocks
                        else if (highlightOak.getValue() &&
                                (block == Blocks.log && isOakVariant(mc, pos) ||
                                        block == Blocks.planks && isOakPlanks(mc, pos))) {
                            newBlocks.add(new BlockPosWithColor(pos, oakColor));
                        }
                    }
                }
            }

            // Update the list of garden blocks
            gardenBlocks.clear();
            gardenBlocks.addAll(newBlocks);
        } catch (Exception e) {
            // Log any exceptions
            System.err.println("Error in CleanPlotESP scanning thread: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isGrassVariant(Minecraft mc, BlockPos pos) {
        // Check metadata for tall grass and double plants
        int metadata = mc.theWorld.getBlockState(pos).getBlock().getMetaFromState(mc.theWorld.getBlockState(pos));
        // For double_plant, variants 2 and 3 are different types of grass
        return metadata == 2 || metadata == 3;
    }

    private boolean isFlowerVariant(Minecraft mc, BlockPos pos) {
        // Check metadata for double plants
        int metadata = mc.theWorld.getBlockState(pos).getBlock().getMetaFromState(mc.theWorld.getBlockState(pos));
        // For double_plant, variants 0, 1, 4, and 5 are flowers
        return metadata == 0 || metadata == 1 || metadata == 4 || metadata == 5;
    }

    private boolean isOakVariant(Minecraft mc, BlockPos pos) {
        // Check metadata for logs to ensure it's oak (metadata 0)
        int metadata = mc.theWorld.getBlockState(pos).getBlock().getMetaFromState(mc.theWorld.getBlockState(pos));
        return (metadata & 3) == 0; // Bottom 2 bits determine wood type, 0 = oak
    }

    private boolean isOakPlanks(Minecraft mc, BlockPos pos) {
        // Check metadata for planks to ensure it's oak (metadata 0)
        int metadata = mc.theWorld.getBlockState(pos).getBlock().getMetaFromState(mc.theWorld.getBlockState(pos));
        return metadata == 0; // 0 = oak planks
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!this.isEnabled() || Minecraft.getMinecraft().thePlayer == null || gardenBlocks.isEmpty()) return;

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

        // Render all found garden blocks
        for (BlockPosWithColor block : gardenBlocks) {
            drawBlockESP(block.pos, block.color, d0, d1, d2);
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