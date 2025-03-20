package atoll.util.robotUtil;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RobotUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double ATTACK_RANGE = 3.0;
    private static final double INTERACTION_RANGE = 3.0;
    private static final int PATH_UPDATE_INTERVAL = 40; // ticks
    private static final int MAX_PATH_LENGTH = 300;
    private static final int MAX_JUMP_HEIGHT = 1;
    private static final int MAX_FALL_HEIGHT = 4;
    private static final double PARKOUR_JUMP_DISTANCE = 4.0;

    private static BlockPos targetPos;
    private static List<BlockPos> currentPath = new ArrayList<>();
    private static int pathUpdateCounter = 0;
    private static boolean isMoving = false;
    private static boolean isAttacking = false;
    private static boolean isParkourMode = false;

    // For stuck detection
    private static Vec3 lastPosition = null;
    private static int stuckCounter = 0;
    private static int stuckJumpCounter = 0;
    private static int consecutiveStuckCount = 0;

    // For movement control
    private static boolean isForwardKeyPressed = false;
    private static boolean isBackKeyPressed = false;
    private static boolean isLeftKeyPressed = false;
    private static boolean isRightKeyPressed = false;
    private static boolean isJumpKeyPressed = false;
    private static boolean isSneakKeyPressed = false;
    private static boolean isSprintKeyPressed = false;

    // For parkour and special movements
    private static boolean isParkourJumping = false;
    private static int parkourJumpTicks = 0;
    private static BlockPos parkourTarget = null;

    // For terrain analysis
    private static Map<BlockPos, TerrainInfo> terrainCache = new HashMap<>();
    private static long lastTerrainCacheClear = 0;

    // For path visualization
    private static List<BlockPos> pathForRendering = new ArrayList<>();

    public static void setTargetPos(int x, int y, int z) {
        targetPos = new BlockPos(x, y, z);
        currentPath.clear();
        pathForRendering.clear();
        pathUpdateCounter = 0;
        isMoving = true;
        stuckCounter = 0;
        consecutiveStuckCount = 0;
        lastPosition = null;
        isParkourJumping = false;
        parkourJumpTicks = 0;
        parkourTarget = null;
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
        isParkourJumping = false;
        parkourJumpTicks = 0;
        parkourTarget = null;
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
        if (System.currentTimeMillis() - lastTerrainCacheClear > 30000) { // 30 seconds
            terrainCache.clear();
            lastTerrainCacheClear = System.currentTimeMillis();
        }

        if (isMoving && targetPos != null) {
            // Check if we've reached the target
            double distToTarget = mc.thePlayer.getDistance(
                    targetPos.getX() + 0.5,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5);

            if (distToTarget < 1.0) {
                stopMovement();
                return;
            }

            // Handle special parkour jump if active
            if (isParkourJumping) {
                handleParkourJump();
                applyMovementControls();
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
            }
        }

        // Handle combat if enabled
        if (isAttacking) {
            handleCombat();
        }

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
        int steps = Math.max(1, (int)(dist / 3));

        for (int i = 1; i <= steps; i++) {
            double t = i / (double)steps;
            int x = start.getX() + (int)(dx * t);
            int y = start.getY() + (int)(dy * t);
            int z = start.getZ() + (int)(dz * t);
            path.add(new BlockPos(x, y, z));
        }

        path.add(end);
        return path;
    }

    private static void navigateAlongPath() {
        if (currentPath.isEmpty()) return;

        BlockPos nextPoint = currentPath.get(0);
        double distToNext = mc.thePlayer.getDistance(
                nextPoint.getX() + 0.5,
                nextPoint.getY(),
                nextPoint.getZ() + 0.5);

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

        // Calculate target yaw
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;

        // Smooth rotation with natural-looking speed
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
        float rotationSpeed = Math.min(5.0F, Math.abs(yawDiff) / 2);

        if (Math.abs(yawDiff) > 3.0F) {
            mc.thePlayer.rotationYaw += Math.signum(yawDiff) * rotationSpeed;
        }

        // Determine movement keys based on yaw difference
        if (Math.abs(yawDiff) < 45.0F) {
            isForwardKeyPressed = true;

            // Sprint if we have a clear path ahead
            if (Math.abs(yawDiff) < 10.0F && horizontalDist > 2.0 && isClearAhead()) {
                isSprintKeyPressed = true;
            }
        } else if (yawDiff > 45.0F && yawDiff < 135.0F) {
            isLeftKeyPressed = true;
        } else if (yawDiff < -45.0F && yawDiff > -135.0F) {
            isRightKeyPressed = true;
        } else {
            isBackKeyPressed = true;
        }

        // Check for parkour opportunities if enabled
        if (isParkourMode && currentPath.size() > 1 && mc.thePlayer.onGround) {
            BlockPos nextNextPoint = currentPath.get(1);
            if (canParkourJumpTo(nextPoint, nextNextPoint)) {
                startParkourJump(nextNextPoint);
                return;
            }
        }

        // Handle jumping for obstacles
        if (dy > 0.1 && mc.thePlayer.onGround) {
            isJumpKeyPressed = true;
        }

        // Handle sneaking for steep drops
        if (dy < -1.0 || isEdgeAhead()) {
            isSneakKeyPressed = true;
        }

        // Look slightly downward when moving to help with placement
        if (isForwardKeyPressed && Math.abs(yawDiff) < 30.0F) {
            mc.thePlayer.rotationPitch = 15.0F;
        }
    }

    private static void handleParkourJump() {
        if (parkourTarget == null) {
            isParkourJumping = false;
            return;
        }

        // Calculate direction to target
        double dx = (parkourTarget.getX() + 0.5) - mc.thePlayer.posX;
        double dz = (parkourTarget.getZ() + 0.5) - mc.thePlayer.posZ;

        // Calculate target yaw
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;

        // Smooth rotation
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
        float rotationSpeed = Math.min(5.0F, Math.abs(yawDiff) / 2);

        if (Math.abs(yawDiff) > 3.0F) {
            mc.thePlayer.rotationYaw += Math.signum(yawDiff) * rotationSpeed;
        }

        // Always move forward during parkour jump
        isForwardKeyPressed = true;

        // Sprint for longer jumps
        isSprintKeyPressed = true;

        // Jump at the right moment
        if (parkourJumpTicks == 0 && mc.thePlayer.onGround) {
            isJumpKeyPressed = true;
        }

        // Increment jump ticks
        parkourJumpTicks++;

        // End parkour jump after a certain time or if we've reached the target
        double distToTarget = mc.thePlayer.getDistance(
                parkourTarget.getX() + 0.5,
                parkourTarget.getY(),
                parkourTarget.getZ() + 0.5);

        if (parkourJumpTicks > 40 || distToTarget < 1.0 || mc.thePlayer.onGround && parkourJumpTicks > 10) {
            isParkourJumping = false;
            parkourJumpTicks = 0;
            parkourTarget = null;
        }
    }

    private static void startParkourJump(BlockPos target) {
        isParkourJumping = true;
        parkourJumpTicks = 0;
        parkourTarget = target;

        // Look at the target
        double dx = (target.getX() + 0.5) - mc.thePlayer.posX;
        double dy = (target.getY() + 0.5) - mc.thePlayer.posY;
        double dz = (target.getZ() + 0.5) - mc.thePlayer.posZ;

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)));

        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
    }

    private static boolean canParkourJumpTo(BlockPos current, BlockPos target) {
        // Calculate horizontal and vertical distance
        double dx = target.getX() - current.getX();
        double dy = target.getY() - current.getY();
        double dz = target.getZ() - current.getZ();
        double horizontalDist = Math.sqrt(dx*dx + dz*dz);

        // Check if the jump is within possible range
        if (horizontalDist > PARKOUR_JUMP_DISTANCE || horizontalDist < 2.0) {
            return false;
        }

        // Check if the vertical difference is reasonable
        if (dy > 1.0 || dy < -1.0) {
            return false;
        }

        // Check if there's a gap between the blocks
        for (int i = 1; i < horizontalDist; i++) {
            double t = i / horizontalDist;
            int x = current.getX() + (int)(dx * t);
            int y = current.getY();
            int z = current.getZ() + (int)(dz * t);

            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.down();

            // If there's a block in the way, we can't jump
            if (!isAirBlock(pos) || !isAirBlock(pos.up())) {
                return false;
            }

            // If there's a block below, it's not a gap
            if (!isAirBlock(below)) {
                return false;
            }
        }

        // Check if the target position is solid to land on
        if (isAirBlock(target.down())) {
            return false;
        }

        // Check if there's enough headroom at the target
        if (!isAirBlock(target) || !isAirBlock(target.up())) {
            return false;
        }

        return true;
    }

    private static boolean isClearAhead() {
        float yaw = mc.thePlayer.rotationYaw;
        double x = -Math.sin(Math.toRadians(yaw));
        double z = Math.cos(Math.toRadians(yaw));

        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        // Check a few blocks ahead
        for (int i = 1; i <= 3; i++) {
            BlockPos pos = new BlockPos(
                    playerPos.getX() + x * i,
                    playerPos.getY(),
                    playerPos.getZ() + z * i);

            BlockPos posUp = pos.up();

            // If there's a block in the way, the path is not clear
            if (!isAirBlock(pos) || !isAirBlock(posUp)) {
                return false;
            }

            // If there's no block below, there might be a drop
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

        // If there's no block below the position ahead, it's an edge
        return isAirBlock(ahead);
    }

    private static void handleCombat() {
        // Find nearest hostile mob
        EntityLivingBase target = findNearestHostileMob();

        if (target != null && mc.thePlayer.getDistanceToEntity(target) <= ATTACK_RANGE) {
            // Calculate rotation to face the target
            double dx = target.posX - mc.thePlayer.posX;
            double dz = target.posZ - mc.thePlayer.posZ;
            double dy = target.posY + target.getEyeHeight() - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());

            double dist = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

            // Smoothly rotate to face target
            float yawDiff = MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
            float pitchDiff = MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch);
            float rotationSpeed = 5.0F;

            if (Math.abs(yawDiff) > 1.0F) {
                mc.thePlayer.rotationYaw += Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), rotationSpeed);
            }

            if (Math.abs(pitchDiff) > 1.0F) {
                mc.thePlayer.rotationPitch += Math.signum(pitchDiff) * Math.min(Math.abs(pitchDiff), rotationSpeed);
            }

            // Attack if facing the target
            if (Math.abs(yawDiff) < 20F && Math.abs(pitchDiff) < 20F) {
                if (mc.thePlayer.ticksExisted % 5 == 0) { // Attack every 5 ticks for legitimate behavior
                    mc.playerController.attackEntity(mc.thePlayer, target);
                    mc.thePlayer.swingItem();
                }
            }
        }
    }

    /**
     * Find nearest hostile mob
     * @return The nearest hostile mob or null if none found
     */
    private static EntityLivingBase findNearestHostileMob() {
        double closestDistance = Double.MAX_VALUE;
        EntityLivingBase closestEntity = null;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityMob && entity.isEntityAlive()) {
                double distance = mc.thePlayer.getDistanceToEntity(entity);
                if (distance < closestDistance && distance <= 10.0) { // Only consider mobs within 10 blocks
                    closestDistance = distance;
                    closestEntity = (EntityLivingBase) entity;
                }
            }
        }

        return closestEntity;
    }

    /**
     * Interact with an NPC menu
     * @param npc The NPC entity to interact with
     */
    public static void interactWithNPC(Entity npc) {
        if (npc != null && mc.thePlayer.getDistanceToEntity(npc) <= INTERACTION_RANGE) {
            mc.playerController.interactWithEntitySendPacket(mc.thePlayer, npc);
        }
    }

    /**
     * Check if the player is stuck
     * @return True if the player hasn't moved significantly for a while
     */
    private static boolean isPlayerStuck() {
        if (lastPosition == null) {
            lastPosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            return false;
        }

        double distMoved = mc.thePlayer.getDistance(lastPosition.xCoord, lastPosition.yCoord, lastPosition.zCoord);
        lastPosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        if (distMoved < 0.1) {
            stuckCounter++;
            return stuckCounter > 30; // Consider stuck after 1.5 seconds of not moving
        } else {
            stuckCounter = 0;
            return false;
        }
    }

    /**
     * Handle a situation where the player is stuck
     */
    private static void handleStuckSituation() {
        consecutiveStuckCount++;

        // Try different strategies based on how long we've been stuck
        if (consecutiveStuckCount == 1) {
            // First try: just jump
            isJumpKeyPressed = true;
        } else if (consecutiveStuckCount == 2) {
            // Second try: jump and move in a random direction
            isJumpKeyPressed = true;
            int random = (int)(Math.random() * 4);
            switch (random) {
                case 0: isForwardKeyPressed = true; break;
                case 1: isBackKeyPressed = true; break;
                case 2: isLeftKeyPressed = true; break;
                case 3: isRightKeyPressed = true; break;
            }
        } else if (consecutiveStuckCount >= 3) {
            // Third try: clear the path and calculate a new one
            currentPath.clear();
            pathForRendering.clear();
            consecutiveStuckCount = 0;
        }
    }

    /**
     * A* pathfinding algorithm with terrain analysis
     * @param start Starting position
     * @param goal Target position
     * @return List of positions forming a path
     */
    private static List<BlockPos> findPath(BlockPos start, BlockPos goal) {
        // Maximum iterations to prevent infinite loops
        final int MAX_ITERATIONS = 2000;

        // If start and goal are the same, return empty path
        if (start.equals(goal)) return new ArrayList<>();

        // Open set - nodes to be evaluated
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(
                Comparator.comparingDouble(node -> node.fCost));

        // Closed set - nodes already evaluated
        Set<BlockPos> closedSet = new HashSet<>();

        // Map to track best path to each node
        Map<BlockPos, PathNode> allNodes = new HashMap<>();

        // Initialize start node
        PathNode startNode = new PathNode(start);
        startNode.gCost = 0;
        startNode.hCost = getHeuristicCost(start, goal);
        startNode.fCost = startNode.hCost;

        openSet.add(startNode);
        allNodes.put(start, startNode);

        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;

            // Get node with lowest fCost
            PathNode current = openSet.poll();

            // If we reached the goal, reconstruct and return the path
            if (current.pos.equals(goal)) {
                List<BlockPos> path = reconstructPath(current);
                if (path.size() > MAX_PATH_LENGTH) {
                    path = path.subList(0, MAX_PATH_LENGTH);
                }
                return path;
            }

            closedSet.add(current.pos);

            // Check all neighbors
            for (BlockPos neighborPos : getNeighbors(current.pos)) {
                // Skip if already evaluated
                if (closedSet.contains(neighborPos)) continue;

                // Calculate cost to this neighbor
                double moveCost = getMoveCost(current.pos, neighborPos);
                double tentativeGCost = current.gCost + moveCost;

                // Get or create neighbor node
                PathNode neighbor = allNodes.getOrDefault(neighborPos, new PathNode(neighborPos));
                allNodes.put(neighborPos, neighbor);

                // If this path is better than any previous one
                if (tentativeGCost < neighbor.gCost) {
                    neighbor.parent = current;
                    neighbor.gCost = tentativeGCost;
                    neighbor.hCost = getHeuristicCost(neighborPos, goal);
                    neighbor.fCost = neighbor.gCost + neighbor.hCost;

                    // Add to open set if not already there
                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    } else {
                        // Update position in queue
                        openSet.remove(neighbor);
                        openSet.add(neighbor);
                    }
                }
            }
        }

        // If no path found, create a simple direct path
        return createDirectPath(start, goal);
    }

    /**
     * Get valid neighboring positions with advanced terrain analysis
     * @param pos Current position
     * @return List of valid neighboring positions
     */
    private static List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();

        // Check horizontal and diagonal neighbors
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;

                // Check if we can move horizontally
                BlockPos newPos = pos.add(x, 0, z);
                if (canStandAt(newPos)) {
                    neighbors.add(newPos);
                }

                // Check if we can move up (climbing)
                for (int y = 1; y <= MAX_JUMP_HEIGHT; y++) {
                    BlockPos upPos = pos.add(x, y, z);
                    if (canStandAt(upPos) && canReach(pos, upPos)) {
                        neighbors.add(upPos);
                        break; // Only add the lowest reachable position
                    }
                }

                // Check if we can move down (falling)
                for (int y = 1; y <= MAX_FALL_HEIGHT; y++) {
                    BlockPos downPos = pos.add(x, -y, z);
                    if (canStandAt(downPos)) {
                        neighbors.add(downPos);
                        break; // Only add the highest position we can fall to
                    }
                }
            }
        }

        // Check for parkour jumps if enabled
        if (isParkourMode) {
            addParkourNeighbors(pos, neighbors);
        }

        return neighbors;
    }

    /**
     * Add potential parkour jump destinations to neighbors
     */
    private static void addParkourNeighbors(BlockPos pos, List<BlockPos> neighbors) {
        //
        // Check for longer jumps in cardinal directions
        for (int dist = 2; dist <= 4; dist++) {
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                BlockPos jumpTarget = pos.offset(facing, dist);

                // Check if we can jump to this position
                if (canStandAt(jumpTarget) &&
                        isAirBlock(jumpTarget.up()) &&
                        isAirBlock(pos.up(2)) &&
                        isAirBlock(pos.offset(facing).up()) &&
                        isAirBlock(pos.offset(facing).up(2))) {

                    // Check if there's a gap between
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

        // Check for diagonal jumps
        for (int x = -2; x <= 2; x += 4) {
            for (int z = -2; z <= 2; z += 4) {
                if (x == 0 && z == 0) continue;

                BlockPos jumpTarget = pos.add(x, 0, z);

                // Check if we can jump to this position
                if (canStandAt(jumpTarget) &&
                        isAirBlock(jumpTarget.up()) &&
                        isAirBlock(pos.up(2))) {

                    // Check if there's a gap between
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

    /**
     * Check if a position is valid for standing
     * @param pos Position to check
     * @return True if the player can stand at this position
     */
    private static boolean canStandAt(BlockPos pos) {
        // Check if the position is already in cache
        if (terrainCache.containsKey(pos)) {
            return terrainCache.get(pos).canStandAt;
        }

        // Check if there's enough space for the player
        boolean hasSpace = isAirBlock(pos) && isAirBlock(pos.up());

        // Check if there's a solid block below to stand on
        boolean hasSolidGround = !isAirBlock(pos.down()) &&
                mc.theWorld.getBlockState(pos.down()).getBlock().isBlockNormalCube();

        // Special case for slabs, stairs, etc.
        if (!hasSolidGround) {
            Block block = mc.theWorld.getBlockState(pos.down()).getBlock();
            hasSolidGround = !isAirBlock(pos.down()) && block.isCollidable();
        }

        // For water, we don't need solid ground
        boolean isWater = !isAirBlock(pos) &&
                mc.theWorld.getBlockState(pos).getBlock().getMaterial().isLiquid();

        boolean canStand = hasSpace && (hasSolidGround || isWater);

        // Cache the result
        TerrainInfo info = new TerrainInfo();
        info.canStandAt = canStand;
        terrainCache.put(pos, info);

        return canStand;
    }

    /**
     * Check if the player can reach from one position to another
     * @param from Starting position
     * @param to Target position
     * @return True if the player can reach the target
     */
    private static boolean canReach(BlockPos from, BlockPos to) {
        // Calculate differences
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();

        // Check vertical difference
        if (dy > MAX_JUMP_HEIGHT) {
            return false;
        }

        // Check if there's a clear path
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
            // For adjacent blocks, check if there's headroom to jump
            return isAirBlock(from.up()) && isAirBlock(from.up(2));
        }

        return false;
    }

    /**
     * Check if a block is air (or other passable block)
     * @param pos Position to check
     * @return True if the block is air or passable
     */
    private static boolean isAirBlock(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock().isPassable(mc.theWorld, pos);
    }

    /**
     * Calculate movement cost between two positions
     * @param from Starting position
     * @param to Ending position
     * @return Movement cost
     */
    private static double getMoveCost(BlockPos from, BlockPos to) {
        // Base cost is the Euclidean distance
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double baseCost = Math.sqrt(dx*dx + dy*dy + dz*dz);

        // Additional cost for vertical movement
        double verticalCost = Math.abs(dy) * 1.5;

        // Additional cost for diagonal movement
        double diagonalCost = (Math.abs(dx) > 0 && Math.abs(dz) > 0) ? 0.4 : 0;

        // Additional cost for water
        boolean isWater = !isAirBlock(to) &&
                mc.theWorld.getBlockState(to).getBlock().getMaterial().isLiquid();
        double waterCost = isWater ? 2.0 : 0;

        // Additional cost for parkour jumps
        double parkourCost = 0;
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
            parkourCost = 3.0;
        }

        return baseCost + verticalCost + diagonalCost + waterCost + parkourCost;
    }

    /**
     * Calculate heuristic cost (estimated cost to goal)
     * @param from Current position
     * @param to Goal position
     * @return Heuristic cost
     */
    private static double getHeuristicCost(BlockPos from, BlockPos to) {
        // Use Manhattan distance as heuristic
        double dx = Math.abs(to.getX() - from.getX());
        double dy = Math.abs(to.getY() - from.getY());
        double dz = Math.abs(to.getZ() - from.getZ());

        // Add a small factor based on straight-line distance to encourage more direct paths
        double euclidean = Math.sqrt(dx*dx + dy*dy + dz*dz);

        return (dx + dy + dz) * 1.1 + euclidean * 0.5;
    }

    /**
     * Reconstruct path from goal to start
     * @param goalNode Goal node
     * @return List of positions forming a path
     */
    private static List<BlockPos> reconstructPath(PathNode goalNode) {
        List<BlockPos> path = new ArrayList<>();
        PathNode current = goalNode;

        // Traverse from goal to start
        while (current != null) {
            path.add(0, current.pos);
            current = current.parent;
        }

        // Optimize the path by removing unnecessary waypoints
        return optimizePath(path);
    }

    /**
     * Optimize path by removing unnecessary waypoints
     * @param path Original path
     * @return Optimized path
     */
    private static List<BlockPos> optimizePath(List<BlockPos> path) {
        if (path.size() <= 2) return path;

        List<BlockPos> optimized = new ArrayList<>();
        optimized.add(path.get(0));

        // Keep track of current direction
        int lastDirX = 0;
        int lastDirY = 0;
        int lastDirZ = 0;

        for (int i = 1; i < path.size() - 1; i++) {
            BlockPos prev = path.get(i - 1);
            BlockPos current = path.get(i);
            BlockPos next = path.get(i + 1);

            // Calculate directions
            int dirX1 = current.getX() - prev.getX();
            int dirY1 = current.getY() - prev.getY();
            int dirZ1 = current.getZ() - prev.getZ();

            int dirX2 = next.getX() - current.getX();
            int dirY2 = next.getY() - current.getY();
            int dirZ2 = next.getZ() - current.getZ();

            // If direction changes or there's a vertical movement, keep this waypoint
            if (dirX1 != dirX2 || dirY1 != dirY2 || dirZ1 != dirZ2 ||
                    dirY1 != 0 || dirY2 != 0 ||
                    (dirX1 != lastDirX || dirY1 != lastDirY || dirZ1 != lastDirZ)) {
                optimized.add(current);
                lastDirX = dirX1;
                lastDirY = dirY1;
                lastDirZ = dirZ1;
            }
        }

        // Always add the final point
        optimized.add(path.get(path.size() - 1));

        return optimized;
    }


    public static List<BlockPos> getPathForRendering() {
        return pathForRendering;
    }

    public static boolean isMoving() {
        return isMoving;
    }

    private static class PathNode {
        BlockPos pos;
        PathNode parent;
        double gCost = Double.MAX_VALUE; // Cost from start to this node
        double hCost = 0; // Heuristic cost from this node to goal
        double fCost = Double.MAX_VALUE; // Total cost (g + h)

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

    private static class TerrainInfo {
        boolean canStandAt = false;
        boolean isJumpable = false;
        boolean isWater = false;
    }
}
