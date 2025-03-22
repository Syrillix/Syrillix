package atoll.util.robotUtil;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RobotUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double ATTACK_RANGE = 3.0;
    private static final double INTERACTION_RANGE = 3.0;
    private static final int PATH_UPDATE_INTERVAL = 20; // ticks
    private static final int MAX_PATH_LENGTH = 300;
    private static final int MAX_JUMP_HEIGHT = 1;
    private static final int MAX_FALL_HEIGHT = 4;
    private static final double PARKOUR_JUMP_DISTANCE = 4.0;
    private static final int TERRAIN_SCAN_RADIUS = 32; // Blocks to scan around player
    private static final int TERRAIN_SCAN_HEIGHT = 10; // Blocks to scan up/down

    private static BlockPos targetPos;
    private static List<BlockPos> currentPath = new ArrayList<>();
    private static int pathUpdateCounter = 0;
    private static boolean isMoving = false;
    private static boolean isAttacking = false;
    private static boolean isParkourMode = false;

    // For stuck detection
    private static Vec3 lastPosition = null;
    private static int stuckCounter = 0;
    private static int consecutiveStuckCount = 0;
    private static long lastStuckTime = 0;
    private static Set<BlockPos> stuckPositions = new HashSet<>();

    // For movement control
    private static boolean isForwardKeyPressed = false;
    private static boolean isBackKeyPressed = false;
    private static boolean isLeftKeyPressed = false;
    private static boolean isRightKeyPressed = false;
    private static boolean isJumpKeyPressed = false;
    private static boolean isSneakKeyPressed = false;
    private static boolean isSprintKeyPressed = false;

    // For smooth camera movement
    private static float targetYaw = 0;
    private static float targetPitch = 0;
    private static float yawSpeed = 0;
    private static float pitchSpeed = 0;
    private static long lastCameraUpdate = 0;
    private static boolean isLookingAround = false;
    private static int lookAroundTicks = 0;

    // For terrain analysis
    private static Map<BlockPos, TerrainInfo> terrainCache = new ConcurrentHashMap<>();
    private static long lastTerrainCacheClear = 0;
    private static long lastTerrainScan = 0;
    private static boolean isTerrainScanned = false;
    private static Set<BlockPos> walkableBlocks = new HashSet<>();
    private static Set<BlockPos> jumpableBlocks = new HashSet<>();
    private static Set<BlockPos> waterBlocks = new HashSet<>();
    private static Set<BlockPos> dangerBlocks = new HashSet<>();

    // For path visualization
    private static List<BlockPos> pathForRendering = new ArrayList<>();

    // For natural movement
    private static int idleTicks = 0;
    private static int randomMovementTicks = 0;
    private static boolean isRandomMovement = false;
    private static float lastMovementVariation = 0;
    private static long lastJumpTime = 0;
    private static int movementStyle = 0; // 0 = normal, 1 = cautious, 2 = aggressive

    public static void setTargetPos(int x, int y, int z) {
        targetPos = new BlockPos(x, y, z);
        currentPath.clear();
        pathForRendering.clear();
        pathUpdateCounter = 0;
        isMoving = true;
        stuckCounter = 0;
        consecutiveStuckCount = 0;
        lastPosition = null;
        isTerrainScanned = false;
        stuckPositions.clear();

        // Reset movement style randomly for variety
        movementStyle = ThreadLocalRandom.current().nextInt(3);

        // Force a terrain scan
        scanTerrain();
    }

    public static BlockPos getTargetPos() {
        return targetPos;
    }

    public static void stopMovement() {
        isMoving = false;
        currentPath.clear();
        pathForRendering.clear();
        targetPos = null;
        resetMovementControls();
        isRandomMovement = false;
    }

    public static void setAttackMode(boolean attack) {
        isAttacking = attack;
    }

    public static void setParkourMode(boolean parkour) {
        isParkourMode = parkour;
    }

    public static void update() {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Reset movement controls at the start of each update
        resetMovementControls();

        // Clear terrain cache periodically
        if (System.currentTimeMillis() - lastTerrainCacheClear > 60000) { // 60 seconds
            terrainCache.clear();
            isTerrainScanned = false;
            lastTerrainCacheClear = System.currentTimeMillis();
        }

        // Scan terrain if needed
        if (!isTerrainScanned || System.currentTimeMillis() - lastTerrainScan > 10000) { // 10 seconds
            scanTerrain();
        }

        // Update camera smoothly
        updateCamera();

        // Handle idle behavior when not moving
        if (!isMoving) {
            handleIdleBehavior();
            applyMovementControls();
            return;
        }

        if (targetPos != null) {
            // Check if we've reached the target
            double distToTarget = getDistanceToPos(targetPos);

            if (distToTarget < 0.8) {
                stopMovement();
                return;
            }

            // Update path periodically or if we're stuck
            boolean isStuck = isPlayerStuck();
            if (pathUpdateCounter <= 0 || currentPath.isEmpty() || isStuck) {
                if (isStuck) {
                    handleStuckSituation();
                }
                calculatePath();
                pathUpdateCounter = PATH_UPDATE_INTERVAL;
            } else {
                pathUpdateCounter--;
            }

            // Navigate along the path
            if (!currentPath.isEmpty()) {
                navigateAlongPath();
            } else if (targetPos != null) {
                // If no path found, try to move directly towards target
                moveDirectlyTowardsTarget();
            }
        }

        // Handle combat if enabled
        if (isAttacking) {
            handleCombat();
        }

        // Add natural movement variations
        addNaturalMovementVariations();

        // Apply movement controls
        applyMovementControls();
    }

    private static void resetMovementControls() {
        isForwardKeyPressed = false;
        isBackKeyPressed = false;
        isLeftKeyPressed = false;
        isRightKeyPressed = false;
        isJumpKeyPressed = false;
        isSneakKeyPressed = false;
        isSprintKeyPressed = false;
    }

    private static void applyMovementControls() {
        // Apply key states
        mc.gameSettings.keyBindForward.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), isForwardKeyPressed);
        mc.gameSettings.keyBindBack.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), isBackKeyPressed);
        mc.gameSettings.keyBindLeft.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), isLeftKeyPressed);
        mc.gameSettings.keyBindRight.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), isRightKeyPressed);
        mc.gameSettings.keyBindJump.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), isJumpKeyPressed);
        mc.gameSettings.keyBindSneak.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), isSneakKeyPressed);
        mc.gameSettings.keyBindSprint.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), isSprintKeyPressed);
    }

    private static void scanTerrain() {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        walkableBlocks.clear();
        jumpableBlocks.clear();
        waterBlocks.clear();
        dangerBlocks.clear();

        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        // Scan area around player
        for (int x = -TERRAIN_SCAN_RADIUS; x <= TERRAIN_SCAN_RADIUS; x++) {
            for (int z = -TERRAIN_SCAN_RADIUS; z <= TERRAIN_SCAN_RADIUS; z++) {
                // Skip blocks that are too far away (circular scan)
                if (x*x + z*z > TERRAIN_SCAN_RADIUS*TERRAIN_SCAN_RADIUS) continue;

                for (int y = -TERRAIN_SCAN_HEIGHT; y <= TERRAIN_SCAN_HEIGHT; y++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    analyzeBlock(pos);
                }
            }
        }

        isTerrainScanned = true;
        lastTerrainScan = System.currentTimeMillis();
    }

    private static void analyzeBlock(BlockPos pos) {
        if (canStandAt(pos)) {
            walkableBlocks.add(pos);

            // Check if this is a jumpable position
            if (isAirBlock(pos.up()) && isAirBlock(pos.up(2))) {
                jumpableBlocks.add(pos);
            }
        }

        // Check for water
        if (!isAirBlock(pos) && mc.theWorld.getBlockState(pos).getBlock().getMaterial().isLiquid()) {
            waterBlocks.add(pos);
        }

        // Check for dangerous blocks (lava, cactus, etc.)
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        Material material = block.getMaterial();
        if (material == Material.lava || material == Material.fire ||
                block.getUnlocalizedName().contains("cactus")) {
            dangerBlocks.add(pos);
        }
    }

    private static void calculatePath() {
        if (targetPos == null) return;

        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        List<BlockPos> newPath = findPath(playerPos, targetPos);

        if (!newPath.isEmpty()) {
            currentPath = newPath;
            pathForRendering = new ArrayList<>(newPath); // Copy for rendering
        } else if (currentPath.isEmpty()) {
            // If no path found and we don't have a current path, try a direct path
            currentPath = createDirectPath(playerPos, targetPos);
            pathForRendering = new ArrayList<>(currentPath);
        }
    }

    private static List<BlockPos> createDirectPath(BlockPos start, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();

        // Calculate direction vector
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();

        // Total distance
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        int steps = Math.max(1, (int)(dist / 2)); // More granular steps

        for (int i = 1; i <= steps; i++) {
            double t = i / (double)steps;
            int x = start.getX() + (int)(dx * t);
            int y = start.getY() + (int)(dy * t);
            int z = start.getZ() + (int)(dz * t);

            // Try to find a valid Y position
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos validPos = findValidYPosition(pos);
            if (validPos != null) {
                path.add(validPos);
            } else {
                path.add(pos); // Add original even if not valid
            }
        }

        path.add(end);
        return path;
    }

    private static BlockPos findValidYPosition(BlockPos pos) {
        // Check current position first
        if (canStandAt(pos)) return pos;

        // Check a few blocks up and down
        for (int y = 1; y <= 3; y++) {
            BlockPos upPos = pos.up(y);
            if (canStandAt(upPos)) return upPos;

            BlockPos downPos = pos.down(y);
            if (canStandAt(downPos)) return downPos;
        }

        return null;
    }

    private static void navigateAlongPath() {
        if (currentPath.isEmpty()) return;

        BlockPos nextPoint = currentPath.get(0);
        double distToNext = getDistanceToPos(nextPoint);

        // If we're close enough to the next point, remove it and move to the next one
        if (distToNext < 0.8) {
            currentPath.remove(0);
            if (currentPath.isEmpty()) return;
            nextPoint = currentPath.get(0);
        }

        // Calculate movement direction
        double dx = (nextPoint.getX() + 0.5) - mc.thePlayer.posX;
        double dy = nextPoint.getY() - mc.thePlayer.posY;
        double dz = (nextPoint.getZ() + 0.5) - mc.thePlayer.posZ;
        double horizontalDist = Math.sqrt(dx*dx + dz*dz);

        // Set target yaw for smooth camera movement
        targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;

        // Calculate yaw difference
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);

        // Look slightly downward when moving
        if (Math.abs(yawDiff) < 30.0F) {
            targetPitch = 10.0F + (float)(Math.random() * 5.0); // Slight randomness
        }

        // Determine movement keys based on yaw difference
        if (Math.abs(yawDiff) < 60.0F) {
            isForwardKeyPressed = true;

            // Sprint if we have a clear path ahead and not in cautious mode
            if (Math.abs(yawDiff) < 20.0F && horizontalDist > 2.0 && isClearAhead() && movementStyle != 1) {
                isSprintKeyPressed = true;
            }
        } else if (yawDiff > 60.0F && yawDiff < 120.0F) {
            isLeftKeyPressed = true;
            if (Math.abs(yawDiff) < 90.0F) isForwardKeyPressed = true;
        } else if (yawDiff < -60.0F && yawDiff > -120.0F) {
            isRightKeyPressed = true;
            if (Math.abs(yawDiff) < 90.0F) isForwardKeyPressed = true;
        } else {
            // If we're facing completely wrong direction, turn in place
            if (Math.abs(yawDiff) > 150.0F) {
                // Randomly choose direction to turn for more natural movement
                if (Math.random() < 0.5) {
                    isLeftKeyPressed = true;
                } else {
                    isRightKeyPressed = true;
                }
            } else {
                isBackKeyPressed = true;
            }
        }

        // Handle jumping for obstacles
        if (dy > 0.1 && mc.thePlayer.onGround) {
            // Add a small random delay before jumping for natural movement
            if (System.currentTimeMillis() - lastJumpTime > 300) {
                isJumpKeyPressed = true;
                lastJumpTime = System.currentTimeMillis();
            }
        }

        // Handle sneaking for steep drops
        if (dy < -1.0 || isEdgeAhead()) {
            isSneakKeyPressed = true;
        }

        // Check for water ahead
        if (isWaterAhead()) {
            // Keep swimming up if underwater
            if (mc.thePlayer.isInWater()) {
                isJumpKeyPressed = true;
            }
        }
    }

    private static void moveDirectlyTowardsTarget() {
        if (targetPos == null) return;

        // Calculate direction to target
        double dx = (targetPos.getX() + 0.5) - mc.thePlayer.posX;
        double dy = targetPos.getY() - mc.thePlayer.posY;
        double dz = (targetPos.getZ() + 0.5) - mc.thePlayer.posZ;
        double horizontalDist = Math.sqrt(dx*dx + dz*dz);

        // Set target yaw for smooth camera movement
        targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;

        // Calculate yaw difference
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);

        // Set target pitch based on vertical difference
        targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));

        // Determine movement keys based on yaw difference
        if (Math.abs(yawDiff) < 45.0F) {
            isForwardKeyPressed = true;
        } else if (yawDiff > 45.0F && yawDiff < 135.0F) {
            isLeftKeyPressed = true;
        } else if (yawDiff < -45.0F && yawDiff > -135.0F) {
            isRightKeyPressed = true;
        } else {
            isBackKeyPressed = true;
        }

        // Handle jumping for obstacles
        if (dy > 0.1 && mc.thePlayer.onGround) {
            isJumpKeyPressed = true;
        }

        // Handle sneaking for steep drops
        if (dy < -1.0 || isEdgeAhead()) {
            isSneakKeyPressed = true;
        }
    }

    private static void updateCamera() {
        // Calculate time delta for smooth movement
        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastCameraUpdate) / 1000.0f;
        lastCameraUpdate = currentTime;

        // Limit delta time to prevent jumps after lag
        deltaTime = Math.min(deltaTime, 0.1f);

        // Calculate yaw difference
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);

        // Calculate pitch difference
        float pitchDiff = targetPitch - mc.thePlayer.rotationPitch;

        // Calculate rotation speeds with natural acceleration/deceleration
        float targetYawSpeed = Math.min(8.0f, Math.abs(yawDiff) * 0.3f);
        float targetPitchSpeed = Math.min(8.0f, Math.abs(pitchDiff) * 0.3f);

        // Smoothly adjust current speed towards target speed
        yawSpeed = lerp(yawSpeed, targetYawSpeed, deltaTime * 5.0f);
        pitchSpeed = lerp(pitchSpeed, targetPitchSpeed, deltaTime * 5.0f);

        // Apply rotation with speed limit
        if (Math.abs(yawDiff) > 0.1f) {
            mc.thePlayer.rotationYaw += Math.signum(yawDiff) * yawSpeed * deltaTime * 50.0f;
        }

        if (Math.abs(pitchDiff) > 0.1f) {
            float newPitch = mc.thePlayer.rotationPitch + Math.signum(pitchDiff) * pitchSpeed * deltaTime * 50.0f;
            // Clamp pitch to prevent looking too far up/down
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(newPitch, -89.0f, 89.0f);
        }

        // Add subtle natural head movement
        if (!isLookingAround && Math.random() < 0.002) {
            isLookingAround = true;
            lookAroundTicks = (int)(Math.random() * 40) + 20;

            // Set random look target
            float randomYaw = mc.thePlayer.rotationYaw + (float)(Math.random() * 30.0 - 15.0);
            float randomPitch = mc.thePlayer.rotationPitch + (float)(Math.random() * 20.0 - 10.0);

            if (isMoving) {
                // Smaller random movements while moving
                randomYaw = mc.thePlayer.rotationYaw + (float)(Math.random() * 10.0 - 5.0);
                randomPitch = mc.thePlayer.rotationPitch + (float)(Math.random() * 6.0 - 3.0);
            }

            targetYaw = randomYaw;
            targetPitch = MathHelper.clamp_float(randomPitch, -80.0f, 80.0f);
        }

        if (isLookingAround) {
            lookAroundTicks--;
            if (lookAroundTicks <= 0) {
                isLookingAround = false;
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    private static void handleIdleBehavior() {
        idleTicks++;

        // Occasionally look around when idle
        if (idleTicks % 100 == 0 && Math.random() < 0.3) {
            isLookingAround = true;
            lookAroundTicks = (int)(Math.random() * 60) + 40;

            // Set random look target
            targetYaw = mc.thePlayer.rotationYaw + (float)(Math.random() * 120.0 - 60.0);
            targetPitch = (float)(Math.random() * 50.0 - 30.0);
        }

        // Occasionally make small random movements
        if (idleTicks % 200 == 0 && Math.random() < 0.2) {
            isRandomMovement = true;
            randomMovementTicks = (int)(Math.random() * 40) + 20;
        }

        if (isRandomMovement) {
            // Random movement pattern
            int pattern = (idleTicks / 10) % 6;

            switch (pattern) {
                case 0:
                    isForwardKeyPressed = true;
                    break;
                case 1:
                    isBackKeyPressed = true;
                    break;
                case 2:
                    isLeftKeyPressed = true;
                    break;
                case 3:
                    isRightKeyPressed = true;
                    break;
                case 4:
                    isForwardKeyPressed = true;
                    isLeftKeyPressed = true;
                    break;
                case 5:
                    isForwardKeyPressed = true;
                    isRightKeyPressed = true;
                    break;
            }

            // Occasionally jump
            if (Math.random() < 0.01 && mc.thePlayer.onGround) {
                isJumpKeyPressed = true;
            }

            randomMovementTicks--;
            if (randomMovementTicks <= 0) {
                isRandomMovement = false;
            }
        }
    }

    private static void addNaturalMovementVariations() {
        if (stuckCounter > 0 || mc.thePlayer.isInWater()) return;

        if (isForwardKeyPressed && mc.thePlayer.onGround) {
            if (mc.thePlayer.ticksExisted % 20 == 0) {
                lastMovementVariation = (float)(Math.random() * 0.2 - 0.1);
            }
            if (lastMovementVariation > 0) {
                isRightKeyPressed = true;
            } else if (lastMovementVariation < 0) {
                isLeftKeyPressed = true;
            }
            if (isSprintKeyPressed && Math.random() < 0.002 && mc.thePlayer.onGround &&
                    System.currentTimeMillis() - lastJumpTime > 2000) {
                isJumpKeyPressed = true;
                lastJumpTime = System.currentTimeMillis();
            }
        }
        if (isForwardKeyPressed && Math.random() < 0.01 && isClearAhead() && movementStyle != 1) {
            isSprintKeyPressed = !isSprintKeyPressed;
        }
    }

    private static boolean isClearAhead() {
        float yaw = mc.thePlayer.rotationYaw;
        double x = -Math.sin(Math.toRadians(yaw));
        double z = Math.cos(Math.toRadians(yaw));

        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        for (int i = 1; i <= 3; i++) {
            BlockPos pos = new BlockPos(
                    playerPos.getX() + x * i,
                    playerPos.getY(),
                    playerPos.getZ() + z * i);

            BlockPos posUp = pos.up();
            if (!isAirBlock(pos) || !isAirBlock(posUp)) {
                return false;
            }
            if (i == 1 && isAirBlock(pos.down())) {
                return false;
            }
        }

        return true;
    }

    private static boolean isEdgeAhead() {
        float yaw = mc.thePlayer.rotationYaw;
        double x = -Math.sin(Math.toRadians(yaw));
        double z = Math.cos(Math.toRadians(yaw));

        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos ahead = new BlockPos(
                playerPos.getX() + x,
                playerPos.getY() - 1,
                playerPos.getZ() + z);
        return isAirBlock(ahead);
    }

    private static boolean isWaterAhead() {
        float yaw = mc.thePlayer.rotationYaw;
        double x = -Math.sin(Math.toRadians(yaw));
        double z = Math.cos(Math.toRadians(yaw));

        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        for (int i = 1; i <= 2; i++) {
            BlockPos ahead = new BlockPos(
                    playerPos.getX() + x * i,
                    playerPos.getY(),
                    playerPos.getZ() + z * i);

            if (!isAirBlock(ahead) &&
                    mc.theWorld.getBlockState(ahead).getBlock().getMaterial().isLiquid()) {
                return true;
            }
        }

        return false;
    }

    private static void handleCombat() {
        EntityLivingBase target = findNearestHostileMob();

        if (target != null && mc.thePlayer.getDistanceToEntity(target) <= ATTACK_RANGE) {
            double dx = target.posX - mc.thePlayer.posX;
            double dz = target.posZ - mc.thePlayer.posZ;
            double dy = target.posY + target.getEyeHeight() - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());

            double dist = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
            targetYaw = yaw;
            targetPitch = pitch;
            float yawDiff = MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
            float pitchDiff = MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch);

            if (Math.abs(yawDiff) < 30F && Math.abs(pitchDiff) < 30F) {
                if (mc.thePlayer.ticksExisted % (3 + ThreadLocalRandom.current().nextInt(5)) == 0) {
                    mc.playerController.attackEntity(mc.thePlayer, target);
                    mc.thePlayer.swingItem();
                }
                if (Math.random() < 0.1) {
                    if (Math.random() < 0.5) {
                        isLeftKeyPressed = true;
                    } else {
                        isRightKeyPressed = true;
                    }
                }
                if (mc.thePlayer.getDistanceToEntity(target) < 2.0 && Math.random() < 0.2) {
                    isBackKeyPressed = true;
                }
                if (Math.random() < 0.05 && mc.thePlayer.onGround) {
                    isJumpKeyPressed = true;
                }
            }
        }
    }

    private static EntityLivingBase findNearestHostileMob() {
        double closestDistance = Double.MAX_VALUE;
        EntityLivingBase closestEntity = null;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityMob && entity.isEntityAlive()) {
                double distance = mc.thePlayer.getDistanceToEntity(entity);
                if (distance < closestDistance && distance <= 10.0) {
                    closestDistance = distance;
                    closestEntity = (EntityLivingBase) entity;
                }
            }
        }

        return closestEntity;
    }

    public static void interactWithNPC(Entity npc) {
        if (npc != null && mc.thePlayer.getDistanceToEntity(npc) <= INTERACTION_RANGE) {
            double dx = npc.posX - mc.thePlayer.posX;
            double dz = npc.posZ - mc.thePlayer.posZ;
            double dy = npc.posY + npc.getEyeHeight() - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());

            double dist = Math.sqrt(dx * dx + dz * dz);
            targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
            targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
            if (Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw)) < 15F) {
                mc.playerController.interactWithEntitySendPacket(mc.thePlayer, npc);
            }
        }
    }

    private static boolean isPlayerStuck() {
        if (lastPosition == null) {
            lastPosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            return false;
        }

        double distMoved = mc.thePlayer.getDistance(lastPosition.xCoord, lastPosition.yCoord, lastPosition.zCoord);
        Vec3 currentPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos currentBlock = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        boolean inKnownStuckPosition = stuckPositions.contains(currentBlock);

        lastPosition = currentPos;

        if (distMoved < 0.05 && isMoving) {
            stuckCounter++;
            boolean isStuck = stuckCounter > 40 || inKnownStuckPosition;
            if (isStuck) {
                stuckPositions.add(currentBlock);
                lastStuckTime = System.currentTimeMillis();
            }
            return isStuck;
        } else {
            stuckCounter = Math.max(0, stuckCounter - 1);
            return false;
        }
    }

    private static void handleStuckSituation() {
        consecutiveStuckCount++;
        if (consecutiveStuckCount == 1) {
            isJumpKeyPressed = true;
        } else if (consecutiveStuckCount == 2) {
            isJumpKeyPressed = true;
            int random = ThreadLocalRandom.current().nextInt(4);
            switch (random) {
                case 0: isForwardKeyPressed = true; break;
                case 1: isBackKeyPressed = true; break;
                case 2: isLeftKeyPressed = true; break;
                case 3: isRightKeyPressed = true; break;
            }
        } else if (consecutiveStuckCount == 3) {
            isJumpKeyPressed = true;
            targetPitch = 30f;
            if (mc.thePlayer.ticksExisted % 5 == 0) {
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
            }
        } else if (consecutiveStuckCount >= 4) {
            currentPath.clear();
            pathForRendering.clear();
            BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -2; z <= 2; z++) {
                        dangerBlocks.add(playerPos.add(x, y, z));
                    }
                }
            }

            consecutiveStuckCount = 0;
        }
    }

    private static double getDistanceToPos(BlockPos pos) {
        return mc.thePlayer.getDistance(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5);
    }

    private static List<BlockPos> findPath(BlockPos start, BlockPos goal) {
        final int MAX_ITERATIONS = 3000;
        if (start.equals(goal)) return new ArrayList<>();
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(
                Comparator.comparingDouble(node -> node.fCost));
        Set<BlockPos> closedSet = new HashSet<>();
        Map<BlockPos, PathNode> allNodes = new HashMap<>();
        PathNode startNode = new PathNode(start);
        startNode.gCost = 0;
        startNode.hCost = getHeuristicCost(start, goal);
        startNode.fCost = startNode.hCost;

        openSet.add(startNode);
        allNodes.put(start, startNode);

        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            PathNode current = openSet.poll();
            if (current.pos.equals(goal) || getDistanceBetween(current.pos, goal) < 2.0) {
                List<BlockPos> path = reconstructPath(current);
                if (path.size() > MAX_PATH_LENGTH) {
                    path = path.subList(0, MAX_PATH_LENGTH);
                }
                return path;
            }

            closedSet.add(current.pos);

            for (BlockPos neighborPos : getNeighbors(current.pos)) {
                if (closedSet.contains(neighborPos)) continue;

                double moveCost = getMoveCost(current.pos, neighborPos);
                double tentativeGCost = current.gCost + moveCost;

                PathNode neighbor = allNodes.getOrDefault(neighborPos, new PathNode(neighborPos));
                allNodes.put(neighborPos, neighbor);

                if (tentativeGCost < neighbor.gCost) {
                    neighbor.parent = current;
                    neighbor.gCost = tentativeGCost;
                    neighbor.hCost = getHeuristicCost(neighborPos, goal);
                    neighbor.fCost = neighbor.gCost + neighbor.hCost;
                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    } else {
                        openSet.remove(neighbor);
                        openSet.add(neighbor);
                    }
                }
            }
        }
        if (getDistanceBetween(start, goal) > 10.0) {
            List<BlockPos> nearGoals = new ArrayList<>();
            for (int x = -5; x <= 5; x++) {
                for (int y = -5; y <= 5; y++) {
                    for (int z = -5; z <= 5; z++) {
                        BlockPos nearGoal = goal.add(x, y, z);
                        if (canStandAt(nearGoal)) {
                            nearGoals.add(nearGoal);
                        }
                    }
                }
            }
            nearGoals.sort(Comparator.comparingDouble(pos -> getDistanceBetween(pos, goal)));

            for (BlockPos nearGoal : nearGoals) {
                List<BlockPos> path = findPath(start, nearGoal);
                if (!path.isEmpty()) {
                    path.add(goal);
                    return path;
                }
            }
        }
        return createDirectPath(start, goal);
    }

    private static double getDistanceBetween(BlockPos pos1, BlockPos pos2) {
        double dx = pos2.getX() - pos1.getX();
        double dy = pos2.getY() - pos1.getY();
        double dz = pos2.getZ() - pos1.getZ();
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private static List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;

                BlockPos newPos = pos.add(x, 0, z);
                if (canStandAt(newPos) && !dangerBlocks.contains(newPos)) {
                    neighbors.add(newPos);
                }

                for (int y = 1; y <= MAX_JUMP_HEIGHT; y++) {
                    BlockPos upPos = pos.add(x, y, z);
                    if (canStandAt(upPos) && canReach(pos, upPos) && !dangerBlocks.contains(upPos)) {
                        neighbors.add(upPos);
                        break;
                    }
                }
                for (int y = 1; y <= MAX_FALL_HEIGHT; y++) {
                    BlockPos downPos = pos.add(x, -y, z);
                    if (canStandAt(downPos) && !dangerBlocks.contains(downPos)) {
                        neighbors.add(downPos);
                        break;
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockPos waterPos = pos.add(x, y, z);
                    if (isWaterBlock(waterPos) && !dangerBlocks.contains(waterPos)) {
                        neighbors.add(waterPos);
                    }
                }
            }
        }
        if (isParkourMode) {
            addParkourNeighbors(pos, neighbors);
        }

        return neighbors;
    }

    private static boolean isWaterBlock(BlockPos pos) {
        return !isAirBlock(pos) &&
                mc.theWorld.getBlockState(pos).getBlock().getMaterial().isLiquid() &&
                mc.theWorld.getBlockState(pos).getBlock().getMaterial() != Material.lava;
    }

    private static void addParkourNeighbors(BlockPos pos, List<BlockPos> neighbors) {
        for (int dist = 2; dist <= 4; dist++) {
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                BlockPos jumpTarget = pos.offset(facing, dist);

                if (canStandAt(jumpTarget) &&
                        isAirBlock(jumpTarget.up()) &&
                        isAirBlock(pos.up(2)) &&
                        isAirBlock(pos.offset(facing).up()) &&
                        isAirBlock(pos.offset(facing).up(2)) &&
                        !dangerBlocks.contains(jumpTarget)) {

                    boolean isGap = true;
                    for (int i = 1; i < dist; i++) {
                        BlockPos midPos = pos.offset(facing, i);
                        if (!isAirBlock(midPos) || !isAirBlock(midPos.down())) {
                            isGap = false;
                            break;
                        }
                    }

                    if (isGap) {
                        neighbors.add(jumpTarget);
                    }
                }
            }
        }

        for (int x = -2; x <= 2; x += 4) {
            for (int z = -2; z <= 2; z += 4) {
                if (x == 0 && z == 0) continue;

                BlockPos jumpTarget = pos.add(x, 0, z);

                if (canStandAt(jumpTarget) &&
                        isAirBlock(jumpTarget.up()) &&
                        isAirBlock(pos.up(2)) &&
                        !dangerBlocks.contains(jumpTarget)) {

                    boolean isGap = true;
                    for (int i = 1; i < Math.max(Math.abs(x), Math.abs(z)); i++) {
                        int dx = (int)(x * (i / (double)Math.max(Math.abs(x), Math.abs(z))));
                        int dz = (int)(z * (i / (double)Math.max(Math.abs(x), Math.abs(z))));
                        BlockPos midPos = pos.add(dx, 0, dz);
                        if (!isAirBlock(midPos) || !isAirBlock(midPos.down())) {
                            isGap = false;
                            break;
                        }
                    }

                    if (isGap) {
                        neighbors.add(jumpTarget);
                    }
                }
            }
        }
    }

    private static boolean canStandAt(BlockPos pos) {
        BlockPos below = pos.down();
        boolean solidBelow = !isAirBlock(below) &&
                !mc.theWorld.getBlockState(below).getBlock().getMaterial().isLiquid();
        boolean spaceForPlayer = isAirBlock(pos) && isAirBlock(pos.up());
        boolean isWater = isWaterBlock(pos);
        return (solidBelow || isWater) && (spaceForPlayer || isWater);
    }

    private static boolean canReach(BlockPos from, BlockPos to) {
        int dy = to.getY() - from.getY();
        if (dy > MAX_JUMP_HEIGHT) return false;

        if (dy > 0) {
            for (int y = 1; y <= 2; y++) {
                if (!isAirBlock(from.up(y))) return false;
            }
            BlockPos midPos = new BlockPos(to.getX(), from.getY(), to.getZ());
            if (!isAirBlock(midPos) || !isAirBlock(midPos.up())) return false;
        }

        return true;
    }

    private static boolean isAirBlock(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock().getMaterial() == Material.air;
    }

    private static double getMoveCost(BlockPos from, BlockPos to) {
        double baseCost = getDistanceBetween(from, to);
        int dy = to.getY() - from.getY();
        if (dy != 0) {
            baseCost *= 1.5;
        }
        if (isWaterBlock(to)) {
            baseCost *= 2.0;
        }
        for (BlockPos danger : dangerBlocks) {
            if (getDistanceBetween(to, danger) < 3.0) {
                baseCost *= 3.0;
                break;
            }
        }

        return baseCost;
    }

    private static double getHeuristicCost(BlockPos from, BlockPos goal) {
        return Math.abs(goal.getX() - from.getX()) +
                Math.abs(goal.getZ() - from.getZ()) +
                Math.abs(goal.getY() - from.getY()) * 1.5;
    }

    private static List<BlockPos> reconstructPath(PathNode endNode) {
        List<BlockPos> path = new ArrayList<>();
        PathNode current = endNode;

        while (current != null) {
            path.add(current.pos);
            current = current.parent;
        }

        Collections.reverse(path);
        return path;
    }



    public static void renderPath() {
        if (pathForRendering.isEmpty()) return;
        double renderX = mc.getRenderManager().viewerPosX;
        double renderY = mc.getRenderManager().viewerPosY;
        double renderZ = mc.getRenderManager().viewerPosZ;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0F);

        GL11.glBegin(GL11.GL_LINE_STRIP);

        for (int i = 0; i < pathForRendering.size(); i++) {
            BlockPos pos = pathForRendering.get(i);

            float progress = i / (float) (pathForRendering.size() - 1);

            GL11.glColor4f(progress, 1.0F - progress, 0.0F, 0.7F);

            GL11.glVertex3d(
                    pos.getX() + 0.5 - renderX,
                    pos.getY() + 0.5 - renderY,
                    pos.getZ() + 0.5 - renderZ);
        }

        GL11.glEnd();

        GL11.glPointSize(5.0F);
        GL11.glBegin(GL11.GL_POINTS);

        for (int i = 0; i < pathForRendering.size(); i++) {
            BlockPos pos = pathForRendering.get(i);

            float progress = i / (float) (pathForRendering.size() - 1);

            GL11.glColor4f(progress, 1.0F - progress, 0.0F, 0.7F);

            GL11.glVertex3d(
                    pos.getX() + 0.5 - renderX,
                    pos.getY() + 0.5 - renderY,
                    pos.getZ() + 0.5 - renderZ);
        }

        GL11.glEnd();

        if (targetPos != null) {
            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.7F);

            double x = targetPos.getX() + 0.5 - renderX;
            double y = targetPos.getY() + 0.5 - renderY;
            double z = targetPos.getZ() + 0.5 - renderZ;
            double size = 0.5;

            GL11.glVertex3d(x - size, y - size, z - size);
            GL11.glVertex3d(x + size, y - size, z - size);

            GL11.glVertex3d(x + size, y - size, z - size);
            GL11.glVertex3d(x + size, y - size, z + size);

            GL11.glVertex3d(x + size, y - size, z + size);
            GL11.glVertex3d(x - size, y - size, z + size);

            GL11.glVertex3d(x - size, y - size, z + size);
            GL11.glVertex3d(x - size, y - size, z - size);

            // Top square
            GL11.glVertex3d(x - size, y + size, z - size);
            GL11.glVertex3d(x + size, y + size, z - size);

            GL11.glVertex3d(x + size, y + size, z - size);
            GL11.glVertex3d(x + size, y + size, z + size);

            GL11.glVertex3d(x + size, y + size, z + size);
            GL11.glVertex3d(x - size, y + size, z + size);

            GL11.glVertex3d(x - size, y + size, z + size);
            GL11.glVertex3d(x - size, y + size, z - size);

            GL11.glVertex3d(x - size, y - size, z - size);
            GL11.glVertex3d(x - size, y + size, z - size);

            GL11.glVertex3d(x + size, y - size, z - size);
            GL11.glVertex3d(x + size, y + size, z - size);

            GL11.glVertex3d(x + size, y - size, z + size);
            GL11.glVertex3d(x + size, y + size, z + size);

            GL11.glVertex3d(x - size, y - size, z + size);
            GL11.glVertex3d(x - size, y + size, z + size);

            GL11.glEnd();
        }
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    // Inner class for A* pathfinding
    private static class PathNode {
        BlockPos pos;
        PathNode parent;
        double gCost = Double.MAX_VALUE;
        double hCost = 0;
        double fCost = Double.MAX_VALUE;

        PathNode(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PathNode other = (PathNode) obj;
            return pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode();
        }
    }
    private static void resetKeys() {
        isForwardKeyPressed = false;
        isBackKeyPressed = false;
        isLeftKeyPressed = false;
        isRightKeyPressed = false;
        isJumpKeyPressed = false;
        isSneakKeyPressed = false;
        isSprintKeyPressed = false;
    }

    private static void applyKeyMovement() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), isForwardKeyPressed);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), isBackKeyPressed);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), isLeftKeyPressed);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), isRightKeyPressed);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), isJumpKeyPressed);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), isSneakKeyPressed);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), isSprintKeyPressed);
        isMoving = isForwardKeyPressed || isBackKeyPressed || isLeftKeyPressed || isRightKeyPressed;
    }
    public static String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("§6RobotUtil Status:§r\n");
        info.append("§7Movement Style: §r").append(getMovementStyleName()).append("\n");
        info.append("§7Path Length: §r").append(currentPath.size()).append("\n");

        if (targetPos != null) {
            info.append("§7Target: §r").append(targetPos.getX()).append(", ")
                    .append(targetPos.getY()).append(", ")
                    .append(targetPos.getZ()).append("\n");
            info.append("§7Distance: §r").append(String.format("%.2f", getDistanceToPos(targetPos))).append("\n");
        }

        info.append("§7Stuck Counter: §r").append(stuckCounter).append("\n");
        info.append("§7Stuck Positions: §r").append(stuckPositions.size()).append("\n");

        return info.toString();
    }

    private static String getMovementStyleName() {
        switch (movementStyle) {
            case 0: return "Natural";
            case 1: return "Careful";
            case 2: return "Fast";
            case 3: return "Parkour";
            default: return "Unknown";
        }
    }
}
