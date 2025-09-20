package com.syrillix.syrillixclient.modules.features.ender;

import com.syrillix.syrillixclient.gui.Category;
import com.syrillix.syrillixclient.gui.Settings.Setting;
import com.syrillix.syrillixclient.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class ZealotFarm extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    // Settings
    private Setting.BooleanSetting showMessages;
    private Setting.SliderSetting maxTargetDistance;
    private Setting.SliderSetting minAttackDelay;
    private Setting.SliderSetting maxAttackDelay;
    private Setting.SliderSetting attacksPerTarget;
    private Setting.BooleanSetting smoothRotation;
    private Setting.BooleanSetting debug;
    private Setting.BooleanSetting continuousAttack;

    // State variables
    private EntityLivingBase currentTarget = null;
    private boolean isAttacking = false;
    private int attackCooldown = 0;
    private int attacksRemaining = 0;
    private long attackStartTime = 0;
    private boolean isAimingAtTarget = false;

    // Rotation variables
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private boolean isRotating = false;
    private long rotationStartTime = 0;
    private long rotationDuration = 0;
    private float startYaw = 0f;
    private float startPitch = 0f;

    // Target body part selection
    private enum BodyPart {
        HEAD, CHEST, LEGS, FEET
    }
    private BodyPart currentBodyPart = BodyPart.CHEST; // Default to chest for better hitbox targeting

    public ZealotFarm() {
        super("ZealotFarm", Keyboard.KEY_NONE, Category.CategoryType.END);

        // Initialize settings
        this.showMessages = new Setting.BooleanSetting("Show Messages", true);
        this.maxTargetDistance = new Setting.SliderSetting("Max Target Distance", 10, 3, 60, 1);
        this.minAttackDelay = new Setting.SliderSetting("Min Attack Delay", 150, 50, 500, 1);
        this.maxAttackDelay = new Setting.SliderSetting("Max Attack Delay", 300, 100, 1000, 1);
        this.attacksPerTarget = new Setting.SliderSetting("Attacks Per Target", 3, 1, 10, 1);
        this.smoothRotation = new Setting.BooleanSetting("Smooth Rotation", true);
        this.debug = new Setting.BooleanSetting("Debug Mode", false);
        this.continuousAttack = new Setting.BooleanSetting("Continuous Attack", true);

        // Register settings
        addSetting(showMessages);
        addSetting(maxTargetDistance);
        addSetting(minAttackDelay);
        addSetting(maxAttackDelay);
        addSetting(attacksPerTarget);
        addSetting(smoothRotation);
        addSetting(continuousAttack);
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

        // Handle rotation to target
        if (isRotating) {
            updateRotation();
        }

        // If we're actively attacking a target
        if (isAttacking && currentTarget != null) {
            handleActiveAttack();
        } else {
            // Not attacking, find a new target
            findNewTarget();
        }
    }

    /**
     * Handles ongoing attack against a target
     */
    private void handleActiveAttack() {
        // Check if target still exists and is alive and is still an enderman/zealot
        if (currentTarget == null || !currentTarget.isEntityAlive() || currentTarget.isDead || !isEndermanOrZealot(currentTarget)) {
            if (showMessages.getValue()) {
                mc.thePlayer.addChatMessage(
                        new ChatComponentText("§b[Atoll] §fTarget no longer valid, finding next..."));
            }
            finishAttacking();
            return;
        }

        // Always update aim at the target but with the actual hitbox positions
        if (smoothRotation.getValue()) {
            lookAtEntityHitbox(currentTarget);
        } else {
            directLookAtEntityHitbox(currentTarget);
        }

        // Only attack if we're properly aiming at the target
        if (isAimingAtTarget) {
            // Attack if cooldown is ready
            if (attackCooldown <= 0) {
                // Attack the target
                attackTarget();

                // In continuous attack mode, keep attacking at regular intervals
                if (continuousAttack.getValue()) {
                    attackCooldown = 4; // Small cooldown between continuous attacks
                } else {
                    // Decrease remaining attacks
                    attacksRemaining--;

                    // If we've done all attacks for this target, move to next
                    if (attacksRemaining <= 0) {
                        if (debug.getValue()) {
                            debugMessage("Completed attacks on current target, finding next...");
                        }
                        finishAttacking();
                        return;
                    }

                    // Reset cooldown for next attack
                    attackCooldown = (int) (minAttackDelay.getValue() +
                            random.nextInt((int) (maxAttackDelay.getValue() - minAttackDelay.getValue() + 1)));
                }

                // Randomly change body part to aim at
                selectRandomBodyPart();

                if (debug.getValue()) {
                    debugMessage("Attacking target: " + getEntityTypeName(currentTarget) +
                            (continuousAttack.getValue() ? " (Continuous)" : " (Remaining: " + attacksRemaining + ")"));
                }
            } else {
                attackCooldown--;
            }
        } else {
            // We're still rotating to aim at the target
            if (debug.getValue() && random.nextInt(20) == 0) { // Limit debug spam
                debugMessage("Still aiming at target...");
            }
        }
    }

    /**
     * Finds a new Enderman target
     */
    private void findNewTarget() {
        if (mc.theWorld == null) return;

        // Find all Endermen and Zealots within range
        List<EntityLivingBase> possibleTargets = mc.theWorld.loadedEntityList.stream()
                .filter(entity -> entity instanceof EntityLivingBase)
                .map(entity -> (EntityLivingBase) entity)
                .filter(this::isEndermanOrZealot)
                .filter(entity -> !entity.isDead && entity.getHealth() > 0)
                .filter(entity -> mc.thePlayer.getDistanceToEntity(entity) <= maxTargetDistance.getValue())
                .filter(entity -> mc.thePlayer.canEntityBeSeen(entity))
                .sorted(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceToEntity(e)))
                .collect(Collectors.toList());

        if (!possibleTargets.isEmpty()) {
            // Target the closest Enderman/Zealot
            currentTarget = possibleTargets.get(0);

            if (debug.getValue()) {
                debugMessage("Targeting " + getEntityTypeName(currentTarget) +
                        " at distance: " + String.format("%.1f", mc.thePlayer.getDistanceToEntity(currentTarget)));
            }

            // Start attacking
            isAttacking = true;
            isAimingAtTarget = false; // Start with aiming flag turned off
            attackStartTime = System.currentTimeMillis();
            attacksRemaining = (int) attacksPerTarget.getValue();
            attackCooldown = 10; // Small initial delay to ensure we're aiming properly first

            // Select random body part to aim at (but prefer center of mass)
            selectRandomBodyPart();
        }
    }

    private boolean isEndermanOrZealot(Entity entity) {
        // Check if it's a vanilla Enderman
        if (entity instanceof EntityEnderman) {
            return true;
        }

        // Check for Hypixel Skyblock Endermen and Zealots by name
        if (entity.hasCustomName()) {
            String name = entity.getCustomNameTag().toLowerCase();
            return name.contains("zealot") || name.contains("special zealot") ||
                    name.contains("enderman") || name.contains("ender");
        }

        // Check entity height and width (Endermen are tall)
        if (entity.height > 2.5f && entity.width < 0.8f) {
            return true;
        }

        return false;
    }

    /**
     * Gets a friendly name for the entity type
     */
    private String getEntityTypeName(Entity entity) {
        if (entity == null) return "Unknown";

        if (entity.hasCustomName()) {
            String name = entity.getCustomNameTag();
            // Extract the actual name from Hypixel format like "[Lv45] Zealot"
            if (name.contains("]")) {
                return name.substring(name.indexOf("]") + 1).trim();
            }
            return name;
        }

        if (entity instanceof EntityEnderman) {
            return "Enderman";
        }

        return entity.getName();
    }

    /**
     * Selects a random body part to aim at, with preference for central hitbox
     */
    private void selectRandomBodyPart() {
        // Higher chance to select center mass for better hitbox targeting
        int rand = random.nextInt(10);
        if (rand < 6) { // 60% chance to aim at chest/center
            currentBodyPart = BodyPart.CHEST;
        } else if (rand < 8) { // 20% chance for head
            currentBodyPart = BodyPart.HEAD;
        } else if (rand < 9) { // 10% chance for legs
            currentBodyPart = BodyPart.LEGS;
        } else { // 10% chance for feet
            currentBodyPart = BodyPart.FEET;
        }

        if (debug.getValue()) {
            debugMessage("Aiming at " + currentBodyPart.name());
        }
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
     * Finishes the attacking phase
     */
    private void finishAttacking() {
        isAttacking = false;
        isAimingAtTarget = false;
        currentTarget = null;
        resetRotation();

        // Immediately look for a new target
        findNewTarget();
    }

    /**
     * Resets targeting state
     */
    private void resetTargeting() {
        currentTarget = null;
        isAttacking = false;
        isAimingAtTarget = false;
        attacksRemaining = 0;
    }

    /**
     * Attacks the current target using right-click
     */
    private void attackTarget() {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Use right-click to attack
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getCurrentEquippedItem());

        // Optional: Add random mouse movement for more human-like behavior
        if (random.nextInt(5) == 0) {
            float jitterAmount = 0.2f;
            mc.thePlayer.rotationYaw += (random.nextFloat() - 0.5f) * jitterAmount;
            mc.thePlayer.rotationPitch += (random.nextFloat() - 0.5f) * jitterAmount;
        }
    }

    /**
     * Looks at the actual entity hitbox instead of the nametag
     */
    private void lookAtEntityHitbox(Entity entity) {
        if (entity == null) return;

        // Calculate center position of entity hitbox
        double targetX = entity.posX;
        double targetZ = entity.posZ;
        double targetY = entity.posY + (entity.height * getBodyPartHeightMultiplier());

        // Add very small random offset for natural aiming (but keep it tight on the hitbox)
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
        startRotation(yaw, pitch, 80 + random.nextInt(30)); // Faster rotation (80-110ms)
    }

    /**
     * Directly rotates to look at entity hitbox without smoothing
     */
    private void directLookAtEntityHitbox(Entity entity) {
        if (entity == null) return;

        // Calculate center position of entity hitbox
        double targetX = entity.posX;
        double targetZ = entity.posZ;
        double targetY = entity.posY + (entity.height * getBodyPartHeightMultiplier());

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
     * Gets the height multiplier based on selected body part
     */
    private float getBodyPartHeightMultiplier() {
        switch (currentBodyPart) {
            case HEAD: return 0.85f;
            case CHEST: return 0.5f;
            case LEGS: return 0.3f;
            case FEET: return 0.1f;
            default: return 0.5f;
        }
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

        // If we're more than 90% done with the rotation, consider it close enough to start attacking
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
     * Checks if the player can see the target entity
     */

}