package com.syrillix.syrillixclient.util.robotUtil.fishing;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.Random;

public class FishUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random random = new Random();

    private static boolean isFishing = false;
    private static long lastCastTime = 0;
    private static long fishBiteTime = 0;
    private static boolean fishBiteDetected = false;
    private static int fishingAttempts = 0;
    private static BlockPos waterTarget = null;

    // Constants for fishing behavior
    private static final int MAX_WATER_SEARCH_RADIUS = 5;
    private static final int MIN_RECAST_DELAY = 1000; // Minimum time between casts in ms
    private static final int MAX_RECAST_DELAY = 3000; // Maximum time between casts in ms
    private static final int MIN_REACTION_TIME = 100; // Minimum reaction time in ms
    private static final int MAX_REACTION_TIME = 400; // Maximum reaction time in ms
    private static final int MAX_FISHING_ATTEMPTS = 5; // Max attempts before finding new spot

    // For smooth rotation
    private static float targetYaw = 0;
    private static float targetPitch = 0;
    private static boolean isRotating = false;
    private static long rotationStartTime = 0;
    private static long rotationDuration = 0;
    private static float startYaw = 0;
    private static float startPitch = 0;

    public static void toggleFishing() {
        isFishing = !isFishing;
        if (isFishing) {
            findWaterTarget();
            fishingAttempts = 0;
        }
    }

    public static boolean isFishing() {
        return isFishing;
    }

    public static void update() {
        if (!isFishing || mc.thePlayer == null || mc.theWorld == null) return;

        // Check if player has a fishing rod
        if (!hasRodInHand()) {
            selectRod();
        }

        // If we don't have a rod at all, stop fishing
        if (!hasRod()) {
            isFishing = false;
            return;
        }

        // Handle rotation to target
        if (isRotating) {
            updateRotation();
        }

        // Check for bite detection
        if (fishBiteDetected && System.currentTimeMillis() >= fishBiteTime) {
            retrieveRod();
            fishBiteDetected = false;

            // Wait a bit before recasting
            int recastDelay = MIN_RECAST_DELAY + random.nextInt(MAX_RECAST_DELAY - MIN_RECAST_DELAY);
            lastCastTime = System.currentTimeMillis() + recastDelay;
            return;
        }

        // Check if we need to find a new water spot
        if (waterTarget == null || fishingAttempts >= MAX_FISHING_ATTEMPTS) {
            findWaterTarget();
            fishingAttempts = 0;
            return;
        }

        // Check if we have a hook in the water
        EntityFishHook fishHook = mc.thePlayer.fishEntity;

        // If no hook and enough time has passed since last cast, cast the rod
        if (fishHook == null && System.currentTimeMillis() > lastCastTime) {
            // Make sure we're looking at the water target before casting
            if (!isRotating) {
                lookAtWater();
            } else if (isLookingAtWater()) {
                castRod();
                fishingAttempts++;
            }
        }

        // Check for bite (exclamation marks in chat)
        checkForBite();
    }

    private static void findWaterTarget() {
        World world = mc.theWorld;
        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        BlockPos bestTarget = null;
        double bestScore = Double.MIN_VALUE;

        // Search for water blocks in a radius around the player
        for (int x = -MAX_WATER_SEARCH_RADIUS; x <= MAX_WATER_SEARCH_RADIUS; x++) {
            for (int z = -MAX_WATER_SEARCH_RADIUS; z <= MAX_WATER_SEARCH_RADIUS; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    // Check if the block is water
                    if (world.getBlockState(pos).getBlock().getMaterial().isLiquid()) {
                        // Check if there's air above the water
                        if (world.isAirBlock(pos.up())) {
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

        waterTarget = bestTarget;
    }

    private static int countOpenWaterAround(BlockPos pos) {
        World world = mc.theWorld;
        int count = 0;

        // Check surrounding blocks
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // Skip the center block

                BlockPos checkPos = pos.add(x, 0, z);
                if (world.getBlockState(checkPos).getBlock().getMaterial().isLiquid() &&
                        world.isAirBlock(checkPos.up())) {
                    count++;
                }
            }
        }

        return count;
    }

    private static void lookAtWater() {
        if (waterTarget == null) return;

        // Calculate the center of the block
        Vec3 targetVec = new Vec3(
                waterTarget.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                waterTarget.getY() + 0.5,
                waterTarget.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3
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

        // Add slight randomization for natural look
        yaw += (random.nextFloat() - 0.5F) * 2.0F;
        pitch += (random.nextFloat() - 0.5F) * 1.0F;

        // Start smooth rotation
        startRotation(yaw, pitch, 200 + random.nextInt(300));
    }

    private static void startRotation(float targetYaw, float targetPitch, long duration) {
        FishUtil.targetYaw = targetYaw;
        FishUtil.targetPitch = targetPitch;
        FishUtil.isRotating = true;
        FishUtil.rotationStartTime = System.currentTimeMillis();
        FishUtil.rotationDuration = duration;
        FishUtil.startYaw = mc.thePlayer.rotationYaw;
        FishUtil.startPitch = mc.thePlayer.rotationPitch;
    }

    private static void updateRotation() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - rotationStartTime;

        if (elapsedTime >= rotationDuration) {
            // Rotation complete
            mc.thePlayer.rotationYaw = targetYaw;
            mc.thePlayer.rotationPitch = targetPitch;
            isRotating = false;
            return;
        }

        // Calculate progress (0.0 to 1.0)
        float progress = (float) elapsedTime / rotationDuration;

        // Apply easing function for smooth acceleration/deceleration
        progress = easeInOutQuad(progress);

        // Interpolate between start and target rotations
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - startYaw);
        float pitchDiff = targetPitch - startPitch;

        mc.thePlayer.rotationYaw = startYaw + yawDiff * progress;
        mc.thePlayer.rotationPitch = startPitch + pitchDiff * progress;
    }

    private static float easeInOutQuad(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float)Math.pow(-2 * t + 2, 2) / 2;
    }

    private static boolean isLookingAtWater() {
        if (waterTarget == null) return false;

        // Get the player's look vector
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 lookVec = mc.thePlayer.getLook(1.0F);
        Vec3 targetVec = playerPos.addVector(lookVec.xCoord * 5, lookVec.yCoord * 5, lookVec.zCoord * 5);

        // Perform ray trace
        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(playerPos, targetVec, true, false, true);

        // Check if we're looking at water
        return result != null &&
                result.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK &&
                mc.theWorld.getBlockState(result.getBlockPos()).getBlock().getMaterial().isLiquid();
    }

    private static void castRod() {
        if (!hasRodInHand()) {
            selectRod();
        }

        // Right click to cast
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getCurrentEquippedItem());
        mc.thePlayer.swingItem();

        lastCastTime = System.currentTimeMillis();
    }

    private static void retrieveRod() {
        if (hasRodInHand()) {
            // Right click to retrieve
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getCurrentEquippedItem());
            mc.thePlayer.swingItem();
        }
    }

    private static boolean hasRod() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemFishingRod) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRodInHand() {
        ItemStack currentItem = mc.thePlayer.getCurrentEquippedItem();
        return currentItem != null && currentItem.getItem() instanceof ItemFishingRod;
    }

    private static void selectRod() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemFishingRod) {
                mc.thePlayer.inventory.currentItem = i;
                break;
            }
        }
    }

    private static void checkForBite() {
        // This method would normally check for the "!!!" title/subtitle
        // Since we can't directly access the title rendering system in this context,
        // we'll use the fishing hook's motion as a proxy

        EntityFishHook fishHook = mc.thePlayer.fishEntity;
        if (fishHook != null) {
            // Check if the hook is in water and has stopped moving horizontally
            // but is bobbing vertically (typical bite behavior)
            if (fishHook.isInWater() &&
                    Math.abs(fishHook.motionX) < 0.01 &&
                    Math.abs(fishHook.motionZ) < 0.01 &&
                    fishHook.motionY < -0.02) {

                // Detect the bite
                if (!fishBiteDetected) {
                    fishBiteDetected = true;

                    // Set a random reaction time (100-400ms)
                    int reactionTime = MIN_REACTION_TIME + random.nextInt(MAX_REACTION_TIME - MIN_REACTION_TIME);
                    fishBiteTime = System.currentTimeMillis() + reactionTime;
                }
            }
        }
    }

    // This method should be called from a title/subtitle event handler
    public static void onTitleOrSubtitle(String text) {
        if (isFishing && text != null && text.contains("!!!")) {
            if (!fishBiteDetected) {
                fishBiteDetected = true;

                // Set a random reaction time (100-400ms)
                int reactionTime = MIN_REACTION_TIME + random.nextInt(MAX_REACTION_TIME - MIN_REACTION_TIME);
                fishBiteTime = System.currentTimeMillis() + reactionTime;
            }
        }
    }
}