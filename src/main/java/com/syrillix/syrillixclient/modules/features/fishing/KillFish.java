package com.syrillix.syrillixclient.modules.features.fishing;

import com.syrillix.syrillixclient.gui.Category;
import com.syrillix.syrillixclient.gui.Settings.Setting;
import com.syrillix.syrillixclient.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class KillFish extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    // Settings
    private Setting.BooleanSetting showMessages;
    private Setting.StringSetting weaponName;
    private Setting.SliderSetting maxTargetDistance;
    private Setting.SliderSetting minAttackDelay;
    private Setting.SliderSetting maxAttackDelay;
    private Setting.BooleanSetting autoSwitch;
    private Setting.BooleanSetting returnToRod;
    private Setting.BooleanSetting smoothRotation;
    private Setting.BooleanSetting debug;
    private Setting.SliderSetting targetDetectionDelay;
    private Setting.BooleanSetting enhancedHypixelDetection;

    // State variables
    private EntityLivingBase currentTarget = null;
    private boolean isAttacking = false;
    private int attackCooldown = 0;
    private int previousHotbarSlot = -1;
    private boolean wasTargetingMob = false;
    private long attackStartTime = 0;
    private int attackAttempts = 0;
    private int maxAttackAttempts = 30; // Safety limit

    // Enhanced detection variables
    private long lastFishCaught = 0;
    private List<Entity> knownEntities = new ArrayList<>();
    private boolean scanningForNewEntities = false;
    private long scanStartTime = 0;
    private List<String> knownMobNames = new ArrayList<>();
    private boolean hasFishingEvent = false;

    // Rotation variables
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private boolean isRotating = false;
    private long rotationStartTime = 0;
    private long rotationDuration = 0;
    private float startYaw = 0f;
    private float startPitch = 0f;

    public KillFish() {
        super("KillFish", Keyboard.KEY_NONE, Category.CategoryType.FISHING);

        // Initialize settings
        this.showMessages = new Setting.BooleanSetting("Show Messages", true);
        this.weaponName = new Setting.StringSetting("Weapon Name", "Frozen Scythe");
        this.maxTargetDistance = new Setting.SliderSetting("Max Target Distance", 10, 3, 20, 1);
        this.minAttackDelay = new Setting.SliderSetting("Min Attack Delay", 150, 50, 500, 1);
        this.maxAttackDelay = new Setting.SliderSetting("Max Attack Delay", 300, 100, 1000, 1);
        this.autoSwitch = new Setting.BooleanSetting("Auto Switch Weapon", true);
        this.returnToRod = new Setting.BooleanSetting("Return to Fishing Rod", true);
        this.smoothRotation = new Setting.BooleanSetting("Smooth Rotation", true);
        this.debug = new Setting.BooleanSetting("Debug Mode", false);
        this.targetDetectionDelay = new Setting.SliderSetting("Target Detection Delay", 500, 100, 2000, 50);
        this.enhancedHypixelDetection = new Setting.BooleanSetting("Enhanced Hypixel Detection", true);

        // Register settings
        addSetting(showMessages);
        addSetting(weaponName);
        addSetting(maxTargetDistance);
        addSetting(minAttackDelay);
        addSetting(maxAttackDelay);
        addSetting(autoSwitch);
        addSetting(returnToRod);
        addSetting(smoothRotation);
        addSetting(targetDetectionDelay);
        addSetting(enhancedHypixelDetection);
        addSetting(debug);
    }

    @Override
    public void onEnable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §a" + getName() + " §fenabled!"));
        }
        resetTargeting();
        refreshEntityList();
    }

    @Override
    public void onDisable() {
        if (showMessages.getValue() && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §c" + getName() + " §fdisabled!"));
        }

        // Return to previous slot if we switched
        if (previousHotbarSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = previousHotbarSlot;
            previousHotbarSlot = -1;
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
            // Continue processing as we can rotate and attack in the same tick
        }

        // Update fishing event detection
        checkForFishingEvents();

        // If we're actively attacking a target
        if (isAttacking && currentTarget != null) {
            handleActiveAttack();
        } else {
            // Not attacking, check if we need to scan for targets
            handleTargetDetection();
        }
    }

    /**
     * Handles ongoing attack against a target
     */
    /**
     * Handles ongoing attack against a target
     */
    private void handleActiveAttack() {
        // Check if target still exists and is alive
        if (currentTarget == null || !currentTarget.isEntityAlive() || currentTarget.isDead) {
            if (showMessages.getValue()) {
                mc.thePlayer.addChatMessage(
                        new ChatComponentText("§b[Atoll] §fTarget defeated!"));
            }
            finishAttacking();
            return;
        }

        // Timeout if we've been attacking too long
        if (System.currentTimeMillis() - attackStartTime > 10000 || attackAttempts > maxAttackAttempts) {
            if (showMessages.getValue()) {
                mc.thePlayer.addChatMessage(
                        new ChatComponentText("§b[Atoll] §cAttack timeout - giving up on target."));
            }
            finishAttacking();
            return;
        }

        // Make sure we're still using the right weapon
        if (autoSwitch.getValue() && !isHoldingWeapon()) {
            switchToWeapon();
        }

        // Always update aim at the target
        if (smoothRotation.getValue()) {
            lookAtEntity(currentTarget);
        } else {
            directLookAtEntity(currentTarget);
        }

        // Attack if cooldown is ready - regardless of where we're looking
        if (attackCooldown <= 0) {
            // Always attack when cooldown is ready
            attackTarget();
            attackCooldown = (int) (minAttackDelay.getValue() +
                    random.nextInt((int) (maxAttackDelay.getValue() - minAttackDelay.getValue())));
            attackAttempts++;

            if (debug.getValue()) {
                debugMessage("Attacking target: " + currentTarget.getName() + " (Attempt #" + attackAttempts + ")");
            }
        } else {
            attackCooldown--;
        }
    }


    /**
     * Detects fishing events and potential targets
     */
    private void handleTargetDetection() {
        // Check if we have a fishing event and should start scanning
        if (hasFishingEvent && !scanningForNewEntities) {
            debugMessage("Starting entity scan after fishing event detected");
            startEntityScan();
        }

        // If we're currently scanning for new entities
        if (scanningForNewEntities) {
            // Check for new entities that appeared
            if (System.currentTimeMillis() - scanStartTime > targetDetectionDelay.getValue()) {
                debugMessage("Scanning for new entities...");
                checkForNewEntities();
                scanningForNewEntities = false;
                hasFishingEvent = false;
            }
        }
    }

    /**
     * Starts scanning for new entities after a fishing event
     */
    private void startEntityScan() {
        refreshEntityList(); // Store current entities
        scanningForNewEntities = true;
        scanStartTime = System.currentTimeMillis();
    }

    /**
     * Stores the current list of entities for comparison
     */
    private void refreshEntityList() {
        if (mc.theWorld == null) return;

        knownEntities.clear();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && entity != mc.thePlayer) {
                knownEntities.add(entity);
                if (entity.hasCustomName()) {
                    knownMobNames.add(entity.getCustomNameTag());
                }
            }
        }
    }

    /**
     * Checks for new entities that appeared after fishing
     */
    private void checkForNewEntities() {
        if (mc.theWorld == null) return;

        List<EntityLivingBase> newEntities = mc.theWorld.loadedEntityList.stream()
                .filter(entity -> entity instanceof EntityLivingBase)
                .map(entity -> (EntityLivingBase) entity)
                .filter(entity -> !knownEntities.contains(entity) && entity != mc.thePlayer)
                // Exclude ArmorStands and Players
                .filter(entity -> !(entity instanceof net.minecraft.entity.item.EntityArmorStand))
                .filter(entity -> !(entity instanceof net.minecraft.entity.player.EntityPlayer))
                .collect(Collectors.toList());

        // Also check for mobs with special names (Hypixel often spawns mobs with custom names)
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity.hasCustomName() && entity instanceof EntityLivingBase && entity != mc.thePlayer
                    // Exclude ArmorStands and Players
                    && !(entity instanceof net.minecraft.entity.item.EntityArmorStand)
                    && !(entity instanceof net.minecraft.entity.player.EntityPlayer)) {
                String name = entity.getCustomNameTag();
                if (!knownMobNames.contains(name)) {
                    if (!newEntities.contains(entity)) {
                        newEntities.add((EntityLivingBase)entity);
                    }
                }
            }
        }

        if (!newEntities.isEmpty()) {
            // Filter by distance and sort
            List<EntityLivingBase> validTargets = newEntities.stream()
                    .filter(entity -> mc.thePlayer.getDistanceToEntity(entity) <= maxTargetDistance.getValue())
                    .filter(entity -> mc.thePlayer.canEntityBeSeen(entity))
                    .sorted(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceToEntity(e)))
                    .collect(Collectors.toList());

            if (!validTargets.isEmpty()) {
                // Target the closest new entity
                currentTarget = validTargets.get(0);

                if (showMessages.getValue()) {
                    mc.thePlayer.addChatMessage(
                            new ChatComponentText("§b[Atoll] §fNew target detected: §e" +
                                    currentTarget.getName() + " §fat distance: §a" +
                                    String.format("%.1f", mc.thePlayer.getDistanceToEntity(currentTarget))));
                }

                // Save current slot before switching
                if (autoSwitch.getValue()) {
                    previousHotbarSlot = mc.thePlayer.inventory.currentItem;
                    switchToWeapon();
                }

                // Start attacking
                isAttacking = true;
                attackStartTime = System.currentTimeMillis();
                attackAttempts = 0;
            }
        }
    }


    /**
     * Checks for fishing events (bobber splash, floating text, etc.)
     */
    private void checkForFishingEvents() {
        boolean foundEvent = false;

        // 1. Check for special entities with "!!!" (Hypixel)
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity != null && entity.hasCustomName()) {
                String name = entity.getCustomNameTag();
                if (name != null && name.contains("!!!")) {
                    foundEvent = true;
                    debugMessage("Detected !!! in entity name: " + name);
                    break;
                }
            }
        }

        // 2. Enhanced detection for Hypixel - look for specific entity patterns
        if (enhancedHypixelDetection.getValue() && !foundEvent) {
            // Check if we're on Hypixel by looking for scoreboard data
            boolean isOnHypixel = isOnHypixelServer();

            if (isOnHypixel) {
                // Hypixel mobs often have specific naming patterns
                for (Entity entity : mc.theWorld.loadedEntityList) {
                    if (entity != null && entity instanceof EntityLivingBase && entity.hasCustomName()) {
                        String name = entity.getCustomNameTag();
                        // Common special mob indicators on Hypixel
                        if (name != null && (name.contains("Sea") || name.contains("Creature") ||
                                name.contains("Monster") || name.contains("Water"))) {
                            foundEvent = true;
                            debugMessage("Detected Hypixel special mob: " + name);
                            break;
                        }
                    }
                }
            }
        }

        // 3. Check if rod was just reeled in (works with AutoFish)
        if (mc.thePlayer.fishEntity == null && System.currentTimeMillis() - lastFishCaught < 2000) {
            foundEvent = true;
            debugMessage("Detected recent fish caught");
        }

        // Update state if fishing event detected
        if (foundEvent && !hasFishingEvent) {
            hasFishingEvent = true;
            lastFishCaught = System.currentTimeMillis();
        }
    }

    /**
     * Attempts to detect if player is on Hypixel server
     */
    private boolean isOnHypixelServer() {
        if (mc.theWorld != null && mc.thePlayer != null) {
            // Check server IP if available
            if (mc.getCurrentServerData() != null) {
                String serverIP = mc.getCurrentServerData().serverIP.toLowerCase();
                return serverIP.contains("hypixel");
            }

            // Try to detect Hypixel-specific scoreboard content
            return mc.theWorld.getScoreboard().getTeams().stream()
                    .anyMatch(team -> team.getRegisteredName().contains("SKYBLOCK"));
        }
        return false;
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
     * Finishes the attacking phase and returns to fishing if enabled
     */
    private void finishAttacking() {
        isAttacking = false;
        currentTarget = null;

        // Return to fishing rod if enabled
        if (returnToRod.getValue() && previousHotbarSlot != -1) {
            mc.thePlayer.inventory.currentItem = previousHotbarSlot;
            previousHotbarSlot = -1;
        }

        resetRotation();
    }

    /**
     * Checks if player is holding the specified weapon
     */
    private boolean isHoldingWeapon() {
        ItemStack currentItem = mc.thePlayer.getCurrentEquippedItem();
        return currentItem != null &&
                currentItem.hasDisplayName() &&
                currentItem.getDisplayName().contains(weaponName.getValue());
    }

    /**
     * Switches to the configured weapon
     */
    private void switchToWeapon() {
        // Try to find the weapon in hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null &&
                    stack.hasDisplayName() &&
                    stack.getDisplayName().contains(weaponName.getValue())) {

                // Remember current slot if we haven't already
                if (previousHotbarSlot == -1) {
                    previousHotbarSlot = mc.thePlayer.inventory.currentItem;
                }

                // Switch to weapon
                mc.thePlayer.inventory.currentItem = i;

                if (showMessages.getValue()) {
                    mc.thePlayer.addChatMessage(
                            new ChatComponentText("§b[Atoll] §fSwitched to §a" +
                                    stack.getDisplayName() + " §fin slot " + (i + 1)));
                }

                return;
            }
        }

        // If weapon not found
        if (showMessages.getValue()) {
            mc.thePlayer.addChatMessage(
                    new ChatComponentText("§b[Atoll] §cWarning: §f'" +
                            weaponName.getValue() + "' not found in hotbar!"));
        }
    }

    /**
     * Resets targeting state
     */
    private void resetTargeting() {
        currentTarget = null;
        isAttacking = false;
        wasTargetingMob = false;
        attackAttempts = 0;
        scanningForNewEntities = false;
        hasFishingEvent = false;
    }

    /**
     * Attacks the current target using right-click
     */
    private void attackTarget() {
        // Frozen Scythe is used with right-click
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getCurrentEquippedItem());
        mc.thePlayer.swingItem();

        // Optional: Add random mouse movement for more human-like behavior
        if (random.nextInt(3) == 0) {
            float jitterAmount = 0.3f;
            mc.thePlayer.rotationYaw += (random.nextFloat() - 0.5f) * jitterAmount;
            mc.thePlayer.rotationPitch += (random.nextFloat() - 0.5f) * jitterAmount;
        }
    }

    /**
     * Checks if player is looking at the target entity
     */
    private boolean isLookingAtEntity(Entity entity) {
        if (entity == null) return false;

        // Ray tracing to check if we're looking at the entity
        Vec3 playerPos = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);
        Vec3 playerLook = mc.thePlayer.getLook(1.0F);
        double reach = 5.0;
        Vec3 rayTraceEnd = playerPos.addVector(
                playerLook.xCoord * reach,
                playerLook.yCoord * reach,
                playerLook.zCoord * reach);

        MovingObjectPosition objectMouseOver = entity.getEntityBoundingBox()
                .calculateIntercept(playerPos, rayTraceEnd);

        return objectMouseOver != null;
    }

    /**
     * Starts a smooth rotation to look at the target entity
     */
    private void lookAtEntity(Entity entity) {
        if (entity == null) return;

        // Calculate angle to entity
        double deltaX = entity.posX - mc.thePlayer.posX;
        double deltaY = (entity.posY + entity.getEyeHeight()) -
                (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double deltaZ = entity.posZ - mc.thePlayer.posZ;

        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        // Normalize angles
        yaw = MathHelper.wrapAngleTo180_float(yaw);
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

        // Add small randomization for natural look
        yaw += (random.nextFloat() - 0.5F) * 2.0F;
        pitch += (random.nextFloat() - 0.5F) * 1.0F;

        // Start smooth rotation
        startRotation(yaw, pitch, 100 + random.nextInt(50));
    }

    /**
     * Directly rotates to look at entity without smoothing
     */
    private void directLookAtEntity(Entity entity) {
        if (entity == null) return;

        // Calculate angle to entity
        double deltaX = entity.posX - mc.thePlayer.posX;
        double deltaY = (entity.posY + entity.getEyeHeight()) -
                (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double deltaZ = entity.posZ - mc.thePlayer.posZ;

        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        // Normalize angles
        yaw = MathHelper.wrapAngleTo180_float(yaw);
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

        // Set rotation directly
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
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
        progress = easeInOutQuad(progress);

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
     * Hook into the AutoFish module - call this when a fish is caught
     */
    public void onFishCaught() {
        lastFishCaught = System.currentTimeMillis();
        hasFishingEvent = true;

        if (debug.getValue()) {
            debugMessage("Fish caught event received from AutoFish");
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        // Detect special entities with indicators that Hypixel uses
        if (mc.theWorld != null) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity != null && entity.hasCustomName()) {
                    String name = entity.getCustomNameTag();
                    if (name != null && name.contains("!!!")) {
                        if (!hasFishingEvent) {
                            hasFishingEvent = true;
                            lastFishCaught = System.currentTimeMillis();
                            debugMessage("Detected fishing indicator from entity name: " + name);
                        }
                    }
                }
            }
        }
    }
}