package atoll.modules.features.garden;

import atoll.gui.Category;
import atoll.gui.setting.Setting;
import atoll.modules.Module;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class AimToCleanBlock extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    // Block position with color data for rendering
    public static class BlockPosWithColor {
        public final BlockPos pos;
        public final int color;

        public BlockPosWithColor(BlockPos pos, int color) {
            this.pos = pos;
            this.color = color;
        }
    }

    // Settings
    private Setting.BooleanSetting showMessages;
    private Setting.SliderSetting maxTargetDistance;
    private Setting.SliderSetting minAimDelay;
    private Setting.SliderSetting maxAimDelay;
    private Setting.BooleanSetting smoothRotation;
    private Setting.BooleanSetting autoInteract;
    private Setting.SliderSetting interactionDelay;
    private Setting.BooleanSetting highlightGrass;
    private Setting.BooleanSetting highlightLeaves;
    private Setting.BooleanSetting highlightFlowers;
    private Setting.BooleanSetting highlightOak;
    private Setting.BooleanSetting debug;

    // State variables
    private BlockPos currentTarget = null;
    private boolean isAiming = false;
    private int aimCooldown = 0;
    private boolean isAimingAtTarget = false;
    private int interactCooldown = 0;
    private List<BlockPosWithColor> targetBlocks = new ArrayList<>();

    // Colors for different block types
    private int grassColor = 0x00FF00; // Green
    private int leavesColor = 0x007700; // Dark green
    private int flowersColor = 0xFF00FF; // Magenta
    private int oakColor = 0x8B4513; // Brown

    // Rotation variables
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private boolean isRotating = false;
    private long rotationStartTime = 0;
    private long rotationDuration = 0;
    private float startYaw = 0f;
    private float startPitch = 0f;

    public AimToCleanBlock() {
        super("AimToCleanBlock", Keyboard.KEY_NONE, Category.CategoryType.GARDEN);

        // Initialize settings
        this.showMessages = new Setting.BooleanSetting("Show Messages", true);
        this.maxTargetDistance = new Setting.SliderSetting("Max Target Distance", 4, 2, 6, 1);
        this.minAimDelay = new Setting.SliderSetting("Min Aim Delay", 10, 1, 20, 1);
        this.maxAimDelay = new Setting.SliderSetting("Max Aim Delay", 15, 2, 25, 1);
        this.smoothRotation = new Setting.BooleanSetting("Smooth Rotation", true);
        this.autoInteract = new Setting.BooleanSetting("Auto Interact", true);
        this.interactionDelay = new Setting.SliderSetting("Interaction Delay", 250, 50, 1000, 10);
        this.highlightGrass = new Setting.BooleanSetting("Target Grass", true);
        this.highlightLeaves = new Setting.BooleanSetting("Target Leaves", true);
        this.highlightFlowers = new Setting.BooleanSetting("Target Flowers", true);
        this.highlightOak = new Setting.BooleanSetting("Target Oak", true);
        this.debug = new Setting.BooleanSetting("Debug Mode", false);

        // Register settings
        addSetting(showMessages);
        addSetting(maxTargetDistance);
        addSetting(minAimDelay);
        addSetting(maxAimDelay);
        addSetting(smoothRotation);
        addSetting(autoInteract);
        addSetting(interactionDelay);
        addSetting(highlightGrass);
        addSetting(highlightLeaves);
        addSetting(highlightFlowers);
        addSetting(highlightOak);
        addSetting(debug);
    }

    @Override
    public void onEnable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §a" + getName() + " §fenabled!"));
        }
        resetTargeting();
    }

    @Override
    public void onDisable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }
        resetTargeting();
        resetRotation();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        // Only process on client tick end to avoid multiple calls per tick
        if (event.phase != TickEvent.Phase.END) return;

        // Update target blocks every few ticks
        if (mc.theWorld.getTotalWorldTime() % 10 == 0) {
            updateTargetBlocks();
        }

        // Handle rotation to target
        if (isRotating) {
            updateRotation();
        }

        // If we're actively aiming at a target
        if (isAiming && currentTarget != null) {
            handleActiveAiming();
        } else {
            // Not aiming, find a new target
            if (aimCooldown <= 0) {
                findNewTarget();
            } else {
                aimCooldown--;
            }
        }
    }

    /**
     * Handles ongoing aiming at a target block
     */
    private void handleActiveAiming() {
        // Check if target still exists and is valid
        if (currentTarget == null || !isValidTargetBlock(currentTarget)) {
            if (showMessages.getValue()) {
                debugMessage("Target no longer valid, finding next...");
            }
            finishAiming();
            return;
        }

        // Always update aim at the target
        if (smoothRotation.getValue()) {
            lookAtBlock(currentTarget);
        } else {
            directLookAtBlock(currentTarget);
        }

        // Only interact if we're properly aiming at the target and auto interact is enabled
        if (isAimingAtTarget && autoInteract.getValue()) {
            // Interact if cooldown is ready
            if (interactCooldown <= 0) {
                interactWithBlock();
                interactCooldown = (int) interactionDelay.getValue();

                if (debug.getValue()) {
                    debugMessage("Interacting with block at " + currentTarget);
                }
            } else {
                interactCooldown--;
            }
        }
    }

    /**
     * Updates the list of target blocks
     */
    private void updateTargetBlocks() {
        targetBlocks.clear();

        int searchRadius = (int) Math.ceil(maxTargetDistance.getValue());
        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    // Check if the block is in range
                    double distance = mc.thePlayer.getDistance(
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5);

                    if (distance > maxTargetDistance.getValue()) {
                        continue;
                    }

                    // Check if the block is valid
                    Block block = mc.theWorld.getBlockState(pos).getBlock();

                    // Check for grass
                    if (highlightGrass.getValue() &&
                            (block == Blocks.tallgrass ||
                                    (block == Blocks.double_plant && isGrassVariant(pos)))) {
                        targetBlocks.add(new BlockPosWithColor(pos, grassColor));
                    }

                    // Check for leaves
                    else if (highlightLeaves.getValue() &&
                            (block == Blocks.leaves || block == Blocks.leaves2)) {
                        targetBlocks.add(new BlockPosWithColor(pos, leavesColor));
                    }

                    // Check for flowers
                    else if (highlightFlowers.getValue() &&
                            (block == Blocks.red_flower || block == Blocks.yellow_flower ||
                                    (block == Blocks.double_plant && isFlowerVariant(pos)))) {
                        targetBlocks.add(new BlockPosWithColor(pos, flowersColor));
                    }

                    // Check for oak blocks
                    else if (highlightOak.getValue() &&
                            ((block == Blocks.log && isOakVariant(pos)) ||
                                    (block == Blocks.planks && isOakPlanks(pos)))) {
                        targetBlocks.add(new BlockPosWithColor(pos, oakColor));
                    }
                }
            }
        }

        if (debug.getValue() && targetBlocks.size() > 0) {
            debugMessage("Found " + targetBlocks.size() + " valid target blocks");
        }
    }

    /**
     * Checks if a double plant is a grass variant
     */
    private boolean isGrassVariant(BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        if (state.getBlock() == Blocks.double_plant) {
            return state.getValue(BlockDoublePlant.VARIANT) == BlockDoublePlant.EnumPlantType.GRASS;
        }
        return false;
    }

    /**
     * Checks if a double plant is a flower variant
     */
    private boolean isFlowerVariant(BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        if (state.getBlock() == Blocks.double_plant) {
            BlockDoublePlant.EnumPlantType type = state.getValue(BlockDoublePlant.VARIANT);
            return type == BlockDoublePlant.EnumPlantType.PAEONIA ||
                    type == BlockDoublePlant.EnumPlantType.ROSE ||
                    type == BlockDoublePlant.EnumPlantType.SYRINGA;
        }
        return false;
    }

    /**
     * Checks if a log is an oak variant
     */
    private boolean isOakVariant(BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        if (state.getBlock() == Blocks.log) {
            // Использование метаданных для определения типа дерева
            // Для дуба метаданные 0, 4, 8 или 12 (в зависимости от ориентации)
            int metadata = state.getBlock().getMetaFromState(state);
            return metadata % 4 == 0; // Проверка на дуб
        }
        return false;
    }

    /**
     * Checks if planks are oak
     */
    private boolean isOakPlanks(BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        if (state.getBlock() == Blocks.planks) {
            return state.getValue(BlockPlanks.VARIANT) == BlockPlanks.EnumType.OAK;
        }
        return false;
    }

    /**
     * Finds a new target block
     */
    private void findNewTarget() {
        if (mc.theWorld == null || targetBlocks.isEmpty()) return;

        // Sort by distance
        targetBlocks.sort(Comparator.comparingDouble(b ->
                mc.thePlayer.getDistance(
                        b.pos.getX() + 0.5,
                        b.pos.getY() + 0.5,
                        b.pos.getZ() + 0.5)));

        // Find the closest valid block that can be seen
        for (BlockPosWithColor blockData : targetBlocks) {
            if (canSeeBlock(blockData.pos)) {
                currentTarget = blockData.pos;

                if (debug.getValue()) {
                    String blockType = getBlockTypeName(blockData.pos);
                    double distance = mc.thePlayer.getDistance(
                            blockData.pos.getX() + 0.5,
                            blockData.pos.getY() + 0.5,
                            blockData.pos.getZ() + 0.5);

                    debugMessage("Targeting " + blockType + " at " +
                            blockData.pos + " (distance: " + String.format("%.2f", distance) + ")");
                }

                // Start aiming
                isAiming = true;
                isAimingAtTarget = false; // Start with aiming flag turned off
                aimCooldown = (int) (minAimDelay.getValue() +
                        random.nextInt((int) (maxAimDelay.getValue() - minAimDelay.getValue() + 1)));
                interactCooldown = (int) (interactionDelay.getValue() / 2); // Start halfway through cooldown
                return;
            }
        }

        if (debug.getValue()) {
            debugMessage("No valid targets found, waiting for next scan");
        }

        // No valid targets found, wait for next scan
        aimCooldown = 5;
    }

    /**
     * Gets a friendly name for the block type
     */
    private String getBlockTypeName(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();

        if (block == Blocks.tallgrass || (block == Blocks.double_plant && isGrassVariant(pos))) {
            return "Grass";
        } else if (block == Blocks.leaves || block == Blocks.leaves2) {
            return "Leaves";
        } else if (block == Blocks.red_flower || block == Blocks.yellow_flower ||
                (block == Blocks.double_plant && isFlowerVariant(pos))) {
            return "Flower";
        } else if ((block == Blocks.log && isOakVariant(pos)) ||
                (block == Blocks.planks && isOakPlanks(pos))) {
            return "Oak";
        }

        return block.getLocalizedName();
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
     * Finishes the aiming phase
     */
    private void finishAiming() {
        isAiming = false;
        isAimingAtTarget = false;
        currentTarget = null;
        resetRotation();

        // Set a small cooldown before finding a new target
        aimCooldown = 10;
    }

    /**
     * Resets targeting state
     */
    private void resetTargeting() {
        currentTarget = null;
        isAiming = false;
        isAimingAtTarget = false;
        targetBlocks.clear();
    }

    /**
     * Interacts with the currently targeted block
     */
    private void interactWithBlock() {
        if (mc.thePlayer == null || mc.theWorld == null || currentTarget == null) return;

        // Calculate the exact position to look at
        Vec3 eyePos = new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);

        Vec3 blockCenter = new Vec3(
                currentTarget.getX() + 0.5,
                currentTarget.getY() + 0.5,
                currentTarget.getZ() + 0.5);

        // Calculate the closest point on the block's surface
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyePos, blockCenter);

        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK &&
                mop.getBlockPos().equals(currentTarget)) {

            // Use left-click to break the block
            mc.playerController.clickBlock(currentTarget, mop.sideHit);

            // Optional: Swing arm for visual feedback
            mc.thePlayer.swingItem();

            // Find a new target after interaction
            finishAiming();
        }
    }

    /**
     * Looks at the block's center with smooth rotation
     */
    private void lookAtBlock(BlockPos blockPos) {
        if (blockPos == null) return;

        // Calculate center position of block
        double targetX = blockPos.getX() + 0.5;
        double targetY = blockPos.getY() + 0.5;
        double targetZ = blockPos.getZ() + 0.5;

        // Add very small random offset for natural aiming
        targetX += (random.nextDouble() - 0.5) * 0.1;
        targetY += (random.nextDouble() - 0.5) * 0.1;
        targetZ += (random.nextDouble() - 0.5) * 0.1;

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
        startRotation(yaw, pitch, 100 + random.nextInt(50)); // 100-150ms rotation time
    }

    /**
     * Directly rotates to look at block without smoothing
     */
    private void directLookAtBlock(BlockPos blockPos) {
        if (blockPos == null) return;

        // Calculate center position of block
        double targetX = blockPos.getX() + 0.5;
        double targetY = blockPos.getY() + 0.5;
        double targetZ = blockPos.getZ() + 0.5;

        // Add very small random offset for natural aiming
        targetX += (random.nextDouble() - 0.5) * 0.05;
        targetY += (random.nextDouble() - 0.5) * 0.05;
        targetZ += (random.nextDouble() - 0.5) * 0.05;

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

        // If we're more than 90% done with the rotation, consider it close enough to start interacting
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
     * Checks if a block is valid for targeting
     */
    private boolean isValidTargetBlock(BlockPos pos) {
        if (mc.theWorld == null) return false;

        Block block = mc.theWorld.getBlockState(pos).getBlock();

        // Check if it's a target block type
        if (highlightGrass.getValue() &&
                (block == Blocks.tallgrass ||
                        (block == Blocks.double_plant && isGrassVariant(pos)))) {
            return true;
        }

        if (highlightLeaves.getValue() &&
                (block == Blocks.leaves || block == Blocks.leaves2)) {
            return true;
        }

        if (highlightFlowers.getValue() &&
                (block == Blocks.red_flower || block == Blocks.yellow_flower ||
                        (block == Blocks.double_plant && isFlowerVariant(pos)))) {
            return true;
        }

        if (highlightOak.getValue() &&
                ((block == Blocks.log && isOakVariant(pos)) ||
                        (block == Blocks.planks && isOakPlanks(pos)))) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the player can see the block
     */
    private boolean canSeeBlock(BlockPos pos) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;

        Vec3 eyePos = new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);

        Vec3 blockCenter = new Vec3(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5);

        // Check if line of sight to the block is clear
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyePos, blockCenter);

        // If no hit or hit the target block, we can see it
        return mop == null ||
                (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK &&
                        mop.getBlockPos().equals(pos));
    }

    /**
     * Optional: Render highlighted blocks in the world
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        // This method would render visual indicators for target blocks
        // Implementation depends on your rendering utilities
    }
}