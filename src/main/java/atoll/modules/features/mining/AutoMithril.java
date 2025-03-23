package atoll.modules.features.mining;

import atoll.gui.Category;
import atoll.gui.setting.Setting;
import atoll.modules.Module;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoMithril extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    // Settings
    private Setting.BooleanSetting showMessages;
    private Setting.SliderSetting range;
    private Setting.SliderSetting minMiningDelay;
    private Setting.SliderSetting maxMiningDelay;
    private Setting.BooleanSetting smoothRotation;
    private Setting.BooleanSetting mineStainedClay;
    private Setting.BooleanSetting mineCyanWool;
    private Setting.BooleanSetting minePrismarine;
    private Setting.BooleanSetting mineLightBlueWool;
    private Setting.BooleanSetting debug;

    // State variables
    private BlockPos targetBlock = null;
    private boolean isMining = false;
    private int miningCooldown = 0;
    private boolean isAimingAtTarget = false;

    // Rotation variables
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private boolean isRotating = false;
    private long rotationStartTime = 0;
    private long rotationDuration = 0;
    private float startYaw = 0f;
    private float startPitch = 0f;

    // Block tracking
    private Map<BlockPos, Long> bedrockBlocks = new ConcurrentHashMap<>();
    private List<BlockPos> potentialBlocks = new CopyOnWriteArrayList<>();

    public AutoMithril() {
        super("AutoMithril", Keyboard.KEY_NONE, Category.CategoryType.MINING);

        // Initialize settings
        this.showMessages = new Setting.BooleanSetting("Show Messages", true);
        this.range = new Setting.SliderSetting("Mining Range", 5, 2, 6, 1);
        this.minMiningDelay = new Setting.SliderSetting("Min Mining Delay", 150, 50, 500, 1);
        this.maxMiningDelay = new Setting.SliderSetting("Max Mining Delay", 300, 100, 1000, 1);
        this.smoothRotation = new Setting.BooleanSetting("Smooth Rotation", true);
        this.mineStainedClay = new Setting.BooleanSetting("Mine Cyan Clay", true);
        this.mineCyanWool = new Setting.BooleanSetting("Mine Cyan Wool", true);
        this.minePrismarine = new Setting.BooleanSetting("Mine Prismarine", true);
        this.mineLightBlueWool = new Setting.BooleanSetting("Mine Light Blue Wool", true);
        this.debug = new Setting.BooleanSetting("Debug Mode", false);

        // Register settings
        addSetting(showMessages);
        addSetting(range);
        addSetting(minMiningDelay);
        addSetting(maxMiningDelay);
        addSetting(smoothRotation);
        addSetting(mineStainedClay);
        addSetting(mineCyanWool);
        addSetting(minePrismarine);
        addSetting(mineLightBlueWool);
        addSetting(debug);
    }

    @Override
    public void onEnable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §a" + getName() + " §fenabled!"));
        }
        resetMining();
    }

    @Override
    public void onDisable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }
        resetMining();
        resetRotation();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        // Only process on client tick end to avoid multiple calls per tick
        if (event.phase != TickEvent.Phase.END) return;

        // Handle rotation to target
        if (isRotating) {
            updateRotation();
        }

        // Update bedrock blocks (check if they've turned back to mithril)
        updateBedrockBlocks();

        // If we're actively mining a target
        if (isMining && targetBlock != null) {
            handleActiveMining();
        } else {
            // Not mining, find a new target
            findNewTarget();
        }
    }

    /**
     * Updates the list of bedrock blocks, removing ones that have changed back
     */
    private void updateBedrockBlocks() {
        Iterator<Map.Entry<BlockPos, Long>> iterator = bedrockBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            BlockPos pos = entry.getKey();
            long timestamp = entry.getValue();

            // Check if block has been bedrock for at least 3 seconds
            if (System.currentTimeMillis() - timestamp > 3000) {
                // Check if the block is no longer bedrock
                Block block = mc.theWorld.getBlockState(pos).getBlock();
                if (block != Blocks.bedrock) {
                    iterator.remove();

                    // If it's a mithril block again, add it to potential targets
                    if (isMithrilBlock(pos)) {
                        potentialBlocks.add(pos);
                        if (debug.getValue()) {
                            debugMessage("Block at " + pos + " regenerated from bedrock");
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles ongoing mining of a target block
     */
    private void handleActiveMining() {
        // Check if target still exists and is valid
        if (targetBlock == null || !isMithrilBlock(targetBlock)) {
            // Check if the block turned into bedrock
            Block block = mc.theWorld.getBlockState(targetBlock).getBlock();
            if (block == Blocks.bedrock) {
                if (debug.getValue()) {
                    debugMessage("Block turned into bedrock, tracking for regeneration");
                }
                bedrockBlocks.put(targetBlock, System.currentTimeMillis());
            }

            finishMining();
            return;
        }

        // Always update aim at the target
        if (smoothRotation.getValue()) {
            lookAtBlock(targetBlock);
        } else {
            directLookAtBlock(targetBlock);
        }

        // Only mine if we're properly aiming at the target
        if (isAimingAtTarget) {
            // Mine if cooldown is ready
            if (miningCooldown <= 0) {
                // Mine the block (left click)
                mineBlock();

                // Reset cooldown for next mining action
                miningCooldown = (int) (minMiningDelay.getValue() +
                        random.nextInt((int) (maxMiningDelay.getValue() - minMiningDelay.getValue() + 1)));

                if (debug.getValue()) {
                    debugMessage("Mining block at " + targetBlock);
                }
            } else {
                miningCooldown--;
            }
        } else {
            // We're still rotating to aim at the target
            if (debug.getValue() && random.nextInt(20) == 0) { // Limit debug spam
                debugMessage("Still aiming at block...");
            }
        }
    }

    /**
     * Finds a new mithril block to mine
     */
    private void findNewTarget() {
        if (mc.theWorld == null) return;

        // First check our list of potential blocks that we've seen regenerate
        if (!potentialBlocks.isEmpty()) {
            Iterator<BlockPos> iterator = potentialBlocks.iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next();
                if (isMithrilBlock(pos) && isInRange(pos) && canSeeBlock(pos)) {
                    targetBlock = pos;
                    iterator.remove();
                    startMining();
                    return;
                } else {
                    iterator.remove(); // Remove invalid blocks
                }
            }
        }

        // If no potential blocks, scan for new ones
        int range = (int) Math.ceil(this.range.getValue());
        BlockPos playerPos = mc.thePlayer.getPosition();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    // Skip if we're already tracking this as bedrock
                    if (bedrockBlocks.containsKey(pos)) continue;

                    if (isMithrilBlock(pos) && isInRange(pos) && canSeeBlock(pos)) {
                        targetBlock = pos;
                        startMining();
                        return;
                    }
                }
            }
        }
    }

    /**
     * Checks if a block is a mithril block we want to mine
     */
    private boolean isMithrilBlock(BlockPos pos) {
        if (mc.theWorld == null) return false;

        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        int metadata = block.getMetaFromState(state);

        // Cyan Stained Clay (metadata 9)
        if (mineStainedClay.getValue() && block == Blocks.stained_hardened_clay && metadata == 9) {
            return true;
        }

        // Cyan Wool (metadata 9)
        if (mineCyanWool.getValue() && block == Blocks.wool && metadata == 9) {
            return true;
        }

        // Light Blue Wool (metadata 3)
        if (mineLightBlueWool.getValue() && block == Blocks.wool && metadata == 3) {
            return true;
        }

        // Prismarine variants
        if (minePrismarine.getValue() && (
                block == Blocks.prismarine)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a block is within mining range
     */
    private boolean isInRange(BlockPos pos) {
        double distSq = mc.thePlayer.getDistanceSq(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5);
        return distSq <= (range.getValue() * range.getValue());
    }

    /**
     * Checks if the player can see a block (not obstructed)
     */
    private boolean canSeeBlock(BlockPos pos) {
        // Get the center of the block
        Vec3 blockCenter = new Vec3(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5);

        // Get player's eye position
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);

        // Check if there's a clear line of sight
        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(
                eyePos,
                blockCenter,
                false,
                true,
                false);

        // If result is null or the position equals our target, we can see it
        return result == null || pos.equals(result.getBlockPos());
    }

    /**
     * Starts mining a new target
     */
    private void startMining() {
        if (targetBlock == null) return;

        isMining = true;
        isAimingAtTarget = false;
        miningCooldown = 5; // Small initial delay

        if (debug.getValue()) {
            debugMessage("Starting to mine block at " + targetBlock);
        }
    }

    /**
     * Finishes mining the current target
     */
    private void finishMining() {
        isMining = false;
        isAimingAtTarget = false;
        targetBlock = null;
        resetRotation();

        // Immediately look for a new target
        findNewTarget();
    }

    /**
     * Resets mining state
     */
    private void resetMining() {
        targetBlock = null;
        isMining = false;
        isAimingAtTarget = false;
    }

    /**
     * Mines the target block by simulating left mouse click
     */
    private void mineBlock() {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Simulate left mouse click
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            // Use our mixin method instead of direct clickMouse()
            leftClick();

            // Optional: Add random mouse movement for more human-like behavior
            if (random.nextInt(5) == 0) {
                float jitterAmount = 0.3f;
                mc.thePlayer.rotationYaw += (random.nextFloat() - 0.5f) * jitterAmount;
                mc.thePlayer.rotationPitch += (random.nextFloat() - 0.5f) * jitterAmount;
            }
        }
    }

    /**
     * Simulates a left mouse click using the mixin
     */
    private void leftClick() {
        try {
            // Получаем метод clickMouse из класса Minecraft
            java.lang.reflect.Method clickMouseMethod = Minecraft.class.getDeclaredMethod("clickMouse");
        
            // Делаем метод доступным, так как он приватный
            clickMouseMethod.setAccessible(true);
        
            // Вызываем метод clickMouse у экземпляра Minecraft
            clickMouseMethod.invoke(mc);
        
            // Имитируем анимацию руки игрока
            mc.thePlayer.swingItem();
        
            if (debug.getValue()) {
                debugMessage("Left click performed using reflection");
            }
        } catch (Exception e) {
            if (debug.getValue()) {
                debugMessage("Failed to perform left click using reflection: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }
    /**
     * Looks at a block with smooth rotation
     */
    /**
     * Looks at a block with smooth rotation
     */
    private void lookAtBlock(BlockPos pos) {
        if (pos == null) return;

        // Get the center of the block with a slight random offset
        double targetX = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        double targetY = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        double targetZ = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;

        // Calculate angle to target position
        double deltaX = targetX - mc.thePlayer.posX;
        double deltaY = targetY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double deltaZ = targetZ - mc.thePlayer.posZ;

        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        // Normalize angles
        yaw = MathHelper.wrapAngleTo180_float(yaw);
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

        // Start smooth rotation
        startRotation(yaw, pitch, 80 + random.nextInt(40)); // 80-120ms rotation
    }

    /**
     * Directly rotates to look at block without smoothing
     */
    private void directLookAtBlock(BlockPos pos) {
        if (pos == null) return;

        // Get the center of the block with a slight random offset
        double targetX = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.1;
        double targetY = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.1;
        double targetZ = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.1;

        // Calculate angle to target position
        double deltaX = targetX - mc.thePlayer.posX;
        double deltaY = targetY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double deltaZ = targetZ - mc.thePlayer.posZ;

        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        // Normalize angles
        yaw = MathHelper.wrapAngleTo180_float(yaw);
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

        // Set rotation directly
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;

        // Instantly mark as aiming at target since we're using direct rotation
        isAimingAtTarget = true;
    }

    /**
     * Starts a smooth rotation to the target angles
     */
    private void startRotation(float targetYaw, float targetPitch, long duration) {
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.isRotating = true;
        this.isAimingAtTarget = false; // Reset aiming flag while rotation is in progress
        this.rotationStartTime = System.currentTimeMillis();
        this.rotationDuration = duration;
        this.startYaw = mc.thePlayer.rotationYaw;
        this.startPitch = mc.thePlayer.rotationPitch;
    }

    /**
     * Updates the player's rotation during a smooth rotation
     */
    private void updateRotation() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - rotationStartTime;

        // Check if we're close enough to the target angles
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
        float pitchDiff = targetPitch - mc.thePlayer.rotationPitch;

        // If we're close enough to target, finish rotation
        if (Math.abs(yawDiff) < 0.3F && Math.abs(pitchDiff) < 0.3F) {
            mc.thePlayer.rotationYaw = targetYaw;
            mc.thePlayer.rotationPitch = targetPitch;
            isRotating = false;
            isAimingAtTarget = true; // Set aiming flag once we've completed rotation
            return;
        }

        // Check if rotation duration has elapsed
        if (elapsedTime >= rotationDuration) {
            // Rotation complete
            mc.thePlayer.rotationYaw = targetYaw;
            mc.thePlayer.rotationPitch = targetPitch;
            isRotating = false;
            isAimingAtTarget = true; // Set aiming flag once we've completed rotation
            return;
        }

        // Calculate progress (0.0 to 1.0)
        float progress = (float) elapsedTime / rotationDuration;

        // Simple easing function that doesn't overshoot
        progress = easeInOutQuad(progress);

        // Interpolate between start and target rotations
        yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - startYaw);
        pitchDiff = targetPitch - startPitch;

        mc.thePlayer.rotationYaw = startYaw + yawDiff * progress;
        mc.thePlayer.rotationPitch = startPitch + pitchDiff * progress;

        // If we're more than 90% done with the rotation, consider it close enough to start mining
        if (progress > 0.9) {
            isAimingAtTarget = true;
        }
    }

    /**
     * Resets rotation to stop any ongoing rotation
     */
    private void resetRotation() {
        isRotating = false;
        isAimingAtTarget = false;
    }

    /**
     * Easing function for smooth movement
     */
    private float easeInOutQuad(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float)Math.pow(-2 * t + 2, 2) / 2;
    }

    /**
     * Debug message if debug mode is enabled
     */
    private void debugMessage(String message) {
        if (debug.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll Debug] §7" + message));
        }
    }

    /**
     * Render event to visualize target blocks
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        // Render current target block
        if (targetBlock != null) {
            renderBlockOutline(targetBlock, Color.GREEN, event.partialTicks);
        }

        // Render bedrock blocks being tracked
        for (BlockPos pos : bedrockBlocks.keySet()) {
            renderBlockOutline(pos, Color.RED, event.partialTicks);
        }

        // Render potential blocks
        for (BlockPos pos : potentialBlocks) {
            renderBlockOutline(pos, Color.BLUE, event.partialTicks);
        }
    }

    /**
     * Renders an outline around a block
     */
    private void renderBlockOutline(BlockPos pos, Color color, float partialTicks) {
        // This is a placeholder for rendering block outlines
        // You would implement this with your rendering system
        // For example, using RenderGlobal.drawSelectionBoundingBox or custom GL rendering
    }

    /**
     * Gets the name of a block for display
     */
    private String getBlockName(BlockPos pos) {
        if (mc.theWorld == null) return "Unknown";

        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        int metadata = block.getMetaFromState(state);

        if (block == Blocks.stained_hardened_clay) {
            return "Stained Clay (Meta: " + metadata + ")";
        } else if (block == Blocks.wool) {
            return "Wool (Meta: " + metadata + ")";
        } else if (block == Blocks.prismarine) {
            return "Prismarine";
        } else if (block == Blocks.bedrock) {
            return "Bedrock";
        } else {
            return block.getLocalizedName();
        }
    }
}
