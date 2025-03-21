package atoll.modules.features.fishing;

import atoll.Main;
import atoll.gui.Category;
import atoll.gui.setting.Setting;
import atoll.modules.features.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Random;

public class AutoFish extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    // Settings
    private Setting.BooleanSetting showMessages;
    private Setting.BooleanSetting autoRecast;
    private Setting.BooleanSetting findOptimalSpot;
    private Setting.SliderSetting minReactionTime;
    private Setting.SliderSetting maxReactionTime;
    private Setting.SliderSetting minRecastDelay;
    private Setting.SliderSetting maxRecastDelay;
    private Setting.BooleanSetting hypixelMode;

    // Fishing state
    private boolean isFishing = false;
    private long lastCastTime = 0;
    private long fishBiteTime = 0;
    private boolean fishBiteDetected = false;
    private int fishingAttempts = 0;
    private BlockPos waterTarget = null;

    // For smooth rotation
    private float targetYaw = 0;
    private float targetPitch = 0;
    private boolean isRotating = false;
    private long rotationStartTime = 0;
    private long rotationDuration = 0;
    private float startYaw = 0;
    private float startPitch = 0;

    // For Hypixel detection
    private long lastSplashTime = 0;
    private boolean splashDetected = false;
    private long hookThrowTime = 0;
    private double lastHookMotionY = 0;
    private int stableTicks = 0;
    private int hookWaterTicks = 0;

    // Constants
    private static final int MAX_WATER_SEARCH_RADIUS = 5;
    private static final int MAX_FISHING_ATTEMPTS = 5;

    public AutoFish() {
        super("AutoFish", Keyboard.KEY_NONE, Category.CategoryType.FISHING);

        // Add settings
        this.showMessages = new Setting.BooleanSetting("Show Messages", true);
        this.autoRecast = new Setting.BooleanSetting("Auto Recast", true);
        this.findOptimalSpot = new Setting.BooleanSetting("Find Optimal Spot", true);
        this.minReactionTime = new Setting.SliderSetting("Min Reaction Time", 150, 100, 300,1);
        this.maxReactionTime = new Setting.SliderSetting("Max Reaction Time", 300, 200, 500,1);
        this.minRecastDelay = new Setting.SliderSetting("Min Recast Delay", 1000, 500, 3000,1);
        this.maxRecastDelay = new Setting.SliderSetting("Max Recast Delay", 3000, 1000, 5000,1);
        this.hypixelMode = new Setting.BooleanSetting("Hypixel Mode", true);

        // Register settings
        addSetting(showMessages);
        addSetting(autoRecast);
        addSetting(findOptimalSpot);
        addSetting(minReactionTime);
        addSetting(maxReactionTime);
        addSetting(minRecastDelay);
        addSetting(maxRecastDelay);
        addSetting(hypixelMode);
    }

    @Override
    public void onEnable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §a" + getName() + " §fenabled!"));
        }

        // Start fishing
        isFishing = true;
        fishingAttempts = 0;
        fishBiteDetected = false;
        splashDetected = false;
        stableTicks = 0;
        hookWaterTicks = 0;

        if (findOptimalSpot.getValue()) {
            findWaterTarget();
        }
    }

    @Override
    public void onDisable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }

        // Stop fishing
        isFishing = false;
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
            return; // Don't process anything else while rotating
        }

        // Get current fishing hook
        EntityFishHook fishHook = mc.thePlayer.fishEntity;

        // Check for bite detection
        if (fishBiteDetected && System.currentTimeMillis() >= fishBiteTime) {
            retrieveRod();
            fishBiteDetected = false;

            // Wait a bit before recasting
            if (autoRecast.getValue()) {
                int recastDelay = (int) (minRecastDelay.getValue() +
                        random.nextInt((int) (maxRecastDelay.getValue() - minRecastDelay.getValue())));
                lastCastTime = System.currentTimeMillis() + recastDelay;
            }
            return;
        }

        // If we're already fishing, just monitor for bites
        if (fishHook != null) {
            // Check for bite using hook physics (Hypixel method)
            if (hypixelMode.getValue()) {
                detectHypixelBite(fishHook);
            }
            return; // Don't do anything else while fishing
        }

        // If we reach here, we're not currently fishing

        // Don't cast if we're on cooldown
        if (System.currentTimeMillis() <= lastCastTime) {
            return;
        }

        // Check if we need to find a new water spot
        if (findOptimalSpot.getValue() && (waterTarget == null || fishingAttempts >= MAX_FISHING_ATTEMPTS)) {
            findWaterTarget();
            fishingAttempts = 0;

            // If we found a target, start rotation
            if (waterTarget != null) {
                lookAtWater();
                lastCastTime = System.currentTimeMillis() + 300; // Small delay before casting
            }
            return;
        }

        // If we have a water target, make sure we're looking at it
        if (findOptimalSpot.getValue() && waterTarget != null) {
            if (!isLookingAtWater()) {
                lookAtWater();
                return;
            }

            // We're looking at water, cast the rod
            if (castRod()) {
                fishingAttempts++;
                hookThrowTime = System.currentTimeMillis();
            } else {
                // If casting failed, try again after a short delay
                lastCastTime = System.currentTimeMillis() + 500;
            }
        } else {
            // Just cast where we're looking
            if (castRod()) {
                fishingAttempts++;
                hookThrowTime = System.currentTimeMillis();
            } else {
                // If casting failed, try again after a short delay
                lastCastTime = System.currentTimeMillis() + 500;
            }
        }
    }

    private boolean isPreciselyLookingAtWater() {
        if (waterTarget == null) return false;

        // Get the player's look vector
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 lookVec = mc.thePlayer.getLook(1.0F);
        Vec3 targetVec = playerPos.addVector(lookVec.xCoord * 5, lookVec.yCoord * 5, lookVec.zCoord * 5);

        // Perform ray trace
        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(playerPos, targetVec, true, false, true);

        // If not looking at any block, return false
        if (result == null || result.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }

        BlockPos lookingAt = result.getBlockPos();

        // Accept the target block itself
        if (lookingAt.equals(waterTarget)) {
            return true;
        }

        // Accept any water block within a tolerance radius
        double distanceSq = lookingAt.distanceSq(waterTarget);
        if (distanceSq <= 2.0) { // Accept blocks within ~1.4 blocks radius
            return mc.theWorld.getBlockState(lookingAt).getBlock().getMaterial().isLiquid();
        }

        return false;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!this.isEnabled() || !hypixelMode.getValue()) return;

        // Check for nametags with "!!!" for Hypixel
        if (mc.theWorld != null) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity != null && entity.hasCustomName()) {
                    String name = entity.getCustomNameTag();
                    if (name != null && name.contains("!!!")) {
                        if (!fishBiteDetected) {
                            fishBiteDetected = true;
                            int reactionTime = (int) (minReactionTime.getValue() +
                                                                random.nextInt((int) (maxReactionTime.getValue() - minReactionTime.getValue())));
                            fishBiteTime = System.currentTimeMillis() + reactionTime;

                            if (showMessages.getValue()) {
                                mc.thePlayer.addChatMessage(
                                        new ChatComponentText("§b[Atoll] §fDetected bite from nametag!"));
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!this.isEnabled()) return;

        // Some servers send chat messages for fishing
        String message = event.message.getUnformattedText();
        if (message.contains("!!!") || message.contains("caught") ||
                message.contains("bite") || message.contains("fish on")) {
            if (!fishBiteDetected) {
                fishBiteDetected = true;
                int reactionTime = (int) (minReactionTime.getValue() +
                                        random.nextInt((int) (maxReactionTime.getValue() - minReactionTime.getValue())));
                fishBiteTime = System.currentTimeMillis() + reactionTime;
            }
        }
    }

    /**
     * Detects fish bites on Hypixel using hook physics
     */
    private void detectHypixelBite(EntityFishHook hook) {
        // Skip detection for the first second after casting
        if (System.currentTimeMillis() - hookThrowTime < 1000) {
            return;
        }

        // Check if hook is in water
        if (hook.isInWater()) {
            hookWaterTicks++;

            // Wait until hook has been in water for a bit
            if (hookWaterTicks < 5) {
                return;
            }

            // Check for hook stability (bobbing in place)
            double motionDiff = Math.abs(hook.motionY - lastHookMotionY);

            // If hook is moving very little horizontally but has vertical motion
            if (Math.abs(hook.motionX) < 0.01 && Math.abs(hook.motionZ) < 0.01) {
                // Check for sudden vertical movement changes (bobber going down)
                if (hook.motionY < -0.04 && motionDiff > 0.03) {
                    if (!splashDetected) {
                        splashDetected = true;
                        lastSplashTime = System.currentTimeMillis();
                    }
                }

                // After splash, wait for stable bobbing
                if (splashDetected && System.currentTimeMillis() - lastSplashTime > 300) {
                    if (motionDiff < 0.02) {
                        stableTicks++;

                        // If stable for a few ticks after splash, it's likely a bite
                        if (stableTicks > 3 && !fishBiteDetected) {
                            fishBiteDetected = true;
                            int reactionTime = (int) (minReactionTime.getValue() +
                                                                random.nextInt((int) (maxReactionTime.getValue() - minReactionTime.getValue())));
                            fishBiteTime = System.currentTimeMillis() + reactionTime;

                            if (showMessages.getValue()) {
                                mc.thePlayer.addChatMessage(
                                        new ChatComponentText("§b[Atoll] §fDetected bite from hook physics!"));
                            }
                        }
                    } else {
                        stableTicks = 0;
                    }
                }
            } else {
                // Reset if hook is moving horizontally
                stableTicks = 0;
            }

            lastHookMotionY = hook.motionY;
        } else {
            // Reset water tracking
            hookWaterTicks = 0;
            splashDetected = false;
            stableTicks = 0;
        }
    }

    private void findWaterTarget() {
        if (mc.theWorld == null) return;

        // Reset fishingAttempts when finding a new target
        fishingAttempts = 0;

        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        BlockPos bestTarget = null;
        double bestScore = Double.MIN_VALUE;

        // Search for water blocks in a radius around the player
        for (int x = -MAX_WATER_SEARCH_RADIUS; x <= MAX_WATER_SEARCH_RADIUS; x++) {
            for (int z = -MAX_WATER_SEARCH_RADIUS; z <= MAX_WATER_SEARCH_RADIUS; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    // Check if the block is water
                    if (mc.theWorld.getBlockState(pos).getBlock().getMaterial().isLiquid()) {
                        // Check if there's air above the water
                        if (mc.theWorld.isAirBlock(pos.up())) {
                            // Calculate score based on distance and open water around
                            double distance = Math.sqrt(pos.distanceSq(playerPos));
                            int openWaterCount = countOpenWaterAround(pos);

                            // Score formula: prefer closer blocks with more open water around
                            double score = openWaterCount - (distance * 0.5);

                            if (score > bestScore) {
                                bestScore = score;
                                bestTarget = pos;
                            }
                        }
                    }
                }
            }
        }

        // Only update if we found a valid target
        if (bestTarget != null) {
            waterTarget = bestTarget;

            if (showMessages.getValue()) {
                mc.thePlayer.addChatMessage(
                        new ChatComponentText("§b[Atoll] §fFound water target at §a" +
                                waterTarget.getX() + ", " + waterTarget.getY() + ", " + waterTarget.getZ()));
            }
        }
    }

    /**
     * Counts open water blocks around a position
     */
    private int countOpenWaterAround(BlockPos pos) {
        int count = 0;

        // Check surrounding blocks
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // Skip the center block

                BlockPos checkPos = pos.add(x, 0, z);
                if (mc.theWorld.getBlockState(checkPos).getBlock().getMaterial().isLiquid() &&
                        mc.theWorld.isAirBlock(checkPos.up())) {
                    count++;
                }
            }
        }

        return count;
    }

/**
 /**
 * Rotates the player to look at the water target
 */
private void lookAtWater() {
    if (waterTarget == null) return;

    // Calculate the center of the block
    Vec3 targetVec = new Vec3(
            waterTarget.getX() + 0.5,
            waterTarget.getY() + 0.5,
            waterTarget.getZ() + 0.5
    );

    // Calculate the direction vector
    double dx = targetVec.xCoord - mc.thePlayer.posX;
    double dy = targetVec.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
    double dz = targetVec.zCoord - mc.thePlayer.posZ;

    // Calculate yaw and pitch
    double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
    float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));

    // Normalize angles
    yaw = MathHelper.wrapAngleTo180_float(yaw);
    pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

    // Add very slight randomization for natural look
    yaw += (random.nextFloat() - 0.5F) * 0.3F;
    pitch += (random.nextFloat() - 0.5F) * 0.2F;

    // Start smooth rotation
    startRotation(yaw, pitch, 200 + random.nextInt(100));
}

    /**
     * Starts a smooth rotation to the target angles
     */
    private void startRotation(float targetYaw, float targetPitch, long duration) {
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.isRotating = true;
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
            return;
        }

        // Check if rotation duration has elapsed
        if (elapsedTime >= rotationDuration) {
            // Rotation complete
            mc.thePlayer.rotationYaw = targetYaw;
            mc.thePlayer.rotationPitch = targetPitch;
            isRotating = false;
            return;
        }

        // Calculate progress (0.0 to 1.0)
        float progress = (float) elapsedTime / rotationDuration;

        // Simple easing function that doesn't overshoot
        progress = progress < 0.5f ? 2 * progress * progress : 1 - (float)Math.pow(-2 * progress + 2, 2) / 2;

        // Interpolate between start and target rotations
        yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - startYaw);
        pitchDiff = targetPitch - startPitch;

        mc.thePlayer.rotationYaw = startYaw + yawDiff * progress;
        mc.thePlayer.rotationPitch = startPitch + pitchDiff * progress;
    }

    /**
     * Resets rotation to stop any ongoing rotation
     */
    private void resetRotation() {
        isRotating = false;
    }

    /**
     * Easing function for smooth movement
     */
    private float easeInOutQuad(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float)Math.pow(-2 * t + 2, 2) / 2;
    }

    /**
     * Checks if the player is looking at water
     */
    private boolean isLookingAtWater() {
        if (waterTarget == null) return false;

        // Get the player's look vector
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 lookVec = mc.thePlayer.getLook(1.0F);
        Vec3 targetVec = playerPos.addVector(lookVec.xCoord * 5, lookVec.yCoord * 5, lookVec.zCoord * 5);

        // Perform ray trace
        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(playerPos, targetVec, true, false, true);

        // If we're not looking at any block, return false
        if (result == null || result.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }

        BlockPos lookingAt = result.getBlockPos();

        // Accept the target block itself
        if (lookingAt.equals(waterTarget)) {
            return true;
        }

        // Accept any water block within a tolerance radius
        double distanceSq = lookingAt.distanceSq(waterTarget);
        if (distanceSq <= 3.0) { // Accept blocks within ~1.7 blocks radius
            return mc.theWorld.getBlockState(lookingAt).getBlock().getMaterial().isLiquid();
        }

        return false;
    }

    /**
     * Casts the fishing rod
     */
    private boolean castRod() {
        if (!hasRodInHand()) {
            selectRod();
            // Give time for the rod selection to register
            return false;
        }

        if (hasRodInHand()) {
            // Right click to cast
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getCurrentEquippedItem());
            mc.thePlayer.swingItem();

            lastCastTime = System.currentTimeMillis() + 500; // Small buffer to prevent rapid casting

            // Reset bite detection variables
            fishBiteDetected = false;
            splashDetected = false;
            stableTicks = 0;
            hookWaterTicks = 0;

            if (showMessages.getValue()) {
                mc.thePlayer.addChatMessage(
                        new ChatComponentText("§b[Atoll] §fCasting fishing rod..."));
            }

            return true;
        }

        return false;
    }

    /**
     * Retrieves the fishing rod (reels in)
     */
    private void retrieveRod() {
        if (hasRodInHand()) {
            // Right click to retrieve
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getCurrentEquippedItem());
            mc.thePlayer.swingItem();

            if (showMessages.getValue()) {
                mc.thePlayer.addChatMessage(
                        new ChatComponentText("§b[Atoll] §fRetrieving fishing rod!"));
            }
        }
    }

    /**
     * Checks if the player has a fishing rod in any hotbar slot
     */
    private boolean hasRod() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemFishingRod) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the player has a fishing rod in hand
     */
    private boolean hasRodInHand() {
        ItemStack currentItem = mc.thePlayer.getCurrentEquippedItem();
        return currentItem != null && currentItem.getItem() instanceof ItemFishingRod;
    }

    /**
     * Selects a fishing rod from the hotbar
     */
    private void selectRod() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemFishingRod) {
                int previousItem = mc.thePlayer.inventory.currentItem;

                // Switch to rod
                mc.thePlayer.inventory.currentItem = i;

                // Add a small delay for legitimacy
                try {
                    Thread.sleep(50 + random.nextInt(100));
                } catch (InterruptedException e) {
                    // Ignore
                }

                if (showMessages.getValue()) {
                    mc.thePlayer.addChatMessage(
                            new ChatComponentText("§b[Atoll] §fSelected fishing rod in slot " + (i + 1)));
                }

                return;
            }
        }
    }
}
