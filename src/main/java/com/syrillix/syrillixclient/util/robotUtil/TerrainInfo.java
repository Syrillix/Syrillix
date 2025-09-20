package com.syrillix.syrillixclient.util.robotUtil;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for analyzing and caching terrain information
 * to assist with pathfinding and movement decisions
 */
public class TerrainInfo {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Cache of block information to reduce world lookups
    private static final Map<BlockPos, BlockInfo> blockCache = new HashMap<>();
    private static final int CACHE_LIFETIME_MS = 5000; // Cache entries expire after 5 seconds
    private static final int MAX_CACHE_SIZE = 1000; // Prevent memory issues

    // Terrain analysis results
    private static List<BlockPos> dangerousBlocks = new ArrayList<>();
    private static List<BlockPos> waterBlocks = new ArrayList<>();
    private static List<BlockPos> climbableBlocks = new ArrayList<>();

    /**
     * Analyzes the terrain around the player to identify key features
     * @param radius The radius around the player to analyze
     */
    public static void analyzeTerrainAroundPlayer(int radius) {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Clear previous analysis
        dangerousBlocks.clear();
        waterBlocks.clear();
        climbableBlocks.clear();

        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        // Scan blocks around player
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    BlockInfo info = getBlockInfo(pos);

                    // Categorize blocks
                    if (info.isDangerous) {
                        dangerousBlocks.add(pos);
                    }

                    if (info.isWater) {
                        waterBlocks.add(pos);
                    }

                    if (info.isClimbable) {
                        climbableBlocks.add(pos);
                    }
                }
            }
        }
    }

    /**
     * Gets information about a block, using cache when possible
     * @param pos The position of the block
     * @return BlockInfo containing properties of the block
     */
    public static BlockInfo getBlockInfo(BlockPos pos) {
        // Check cache first
        long currentTime = System.currentTimeMillis();
        if (blockCache.containsKey(pos)) {
            BlockInfo cachedInfo = blockCache.get(pos);
            if (currentTime - cachedInfo.timestamp < CACHE_LIFETIME_MS) {
                return cachedInfo;
            }
        }

        // Cache miss or expired, get fresh data
        BlockInfo info = analyzeBlock(pos);

        // Update cache
        blockCache.put(pos, info);

        // Clean cache if it's too large
        if (blockCache.size() > MAX_CACHE_SIZE) {
            cleanCache();
        }

        return info;
    }

    /**
     * Analyzes a block to determine its properties
     * @param pos The position of the block
     * @return BlockInfo containing properties of the block
     */
    private static BlockInfo analyzeBlock(BlockPos pos) {
        World world = mc.theWorld;
        if (world == null) return new BlockInfo(false, false, false, false, false);

        Block block = world.getBlockState(pos).getBlock();
        Material material = block.getMaterial();

        boolean isSolid = material.isSolid();
        boolean isWater = material == Material.water;
        boolean isLava = material == Material.lava;
        boolean isClimbable = block.isLadder(world, pos, mc.thePlayer) || material == Material.vine;
        boolean isDangerous = isLava || material == Material.fire ||
                block.getUnlocalizedName().contains("cactus");

        return new BlockInfo(isSolid, isWater, isClimbable, isDangerous, false);
    }

    /**
     * Removes old entries from the block cache
     */
    private static void cleanCache() {
        long currentTime = System.currentTimeMillis();
        blockCache.entrySet().removeIf(entry ->
                currentTime - entry.getValue().timestamp > CACHE_LIFETIME_MS);
    }

    /**
     * Checks if a position is safe to stand on
     * @param pos The position to check
     * @return true if the position is safe
     */
    public static boolean isSafeToStandAt(BlockPos pos) {
        // Check if the block at feet level is air or non-solid
        BlockInfo feetInfo = getBlockInfo(pos);
        if (feetInfo.isSolid && !feetInfo.isClimbable) return false;

        // Check if the block at head level is air or non-solid
        BlockInfo headInfo = getBlockInfo(pos.up());
        if (headInfo.isSolid && !headInfo.isClimbable) return false;

        // Check if there's something solid below
        BlockInfo belowInfo = getBlockInfo(pos.down());
        boolean hasSolidGround = belowInfo.isSolid || belowInfo.isClimbable;

        // Special case for water
        boolean isInWater = feetInfo.isWater || headInfo.isWater;

        // Check for dangerous blocks nearby
        boolean isDangerNearby = isDangerousBlockNearby(pos);

        return (hasSolidGround || isInWater) && !isDangerNearby;
    }

    /**
     * Checks if there are dangerous blocks adjacent to the position
     * @param pos The position to check around
     * @return true if there are dangerous blocks nearby
     */
    public static boolean isDangerousBlockNearby(BlockPos pos) {
        // Check adjacent blocks
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockPos checkPos = pos.add(x, y, z);
                    BlockInfo info = getBlockInfo(checkPos);

                    if (info.isDangerous) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks if there's a clear path between two positions
     * @param start The starting position
     * @param end The ending position
     * @return true if there's a clear path
     */
    public static boolean hasClearPath(BlockPos start, BlockPos end) {
        // Simple line-of-sight check
        int steps = (int) Math.ceil(start.distanceSq(end));

        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();

        for (int i = 1; i < steps; i++) {
            double progress = i / (double) steps;

            int x = start.getX() + (int) (dx * progress);
            int y = start.getY() + (int) (dy * progress);
            int z = start.getZ() + (int) (dz * progress);

            BlockPos pos = new BlockPos(x, y, z);
            BlockInfo info = getBlockInfo(pos);

            if (info.isSolid && !info.isClimbable && !info.isWater) {
                return false;
            }
        }

        return true;
    }

    /**
     * Finds the nearest safe position to a target
     * @param target The target position
     * @param maxRadius The maximum search radius
     * @return The nearest safe position, or null if none found
     */
    public static BlockPos findNearestSafePosition(BlockPos target, int maxRadius) {
        // Check the target itself first
        if (isSafeToStandAt(target)) {
            return target;
        }

        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        // Search in expanding squares around the target
        for (int r = 1; r <= maxRadius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    // Only check the perimeter of the square
                    if (Math.abs(x) < r && Math.abs(z) < r) continue;

                    // Check a range of Y values
                    for (int y = -r; y <= r; y++) {
                        BlockPos pos = target.add(x, y, z);

                        if (isSafeToStandAt(pos)) {
                            double distance = pos.distanceSq(target);
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                bestPos = pos;
                            }
                        }
                    }
                }
            }

            // If we found a position in this radius, return it
            if (bestPos != null) {
                return bestPos;
            }
        }

        return null;
    }

    /**
     * Analyzes the terrain to find potential parkour jumps
     * @param pos The starting position
     * @param maxDistance The maximum jump distance to consider
     * @return A list of positions that can be reached via parkour jumps
     */
    public static List<BlockPos> findParkourJumps(BlockPos pos, int maxDistance) {
        List<BlockPos> jumpPositions = new ArrayList<>();

        // Check in all horizontal directions
        for (int x = -maxDistance; x <= maxDistance; x++) {
            for (int z = -maxDistance; z <= maxDistance; z++) {
                // Skip the center and adjacent blocks
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) continue;

                // Skip if too far
                if (Math.sqrt(x*x + z*z) > maxDistance) continue;

                // Check different heights
                for (int y = -1; y <= 2; y++) {
                    BlockPos targetPos = pos.add(x, y, z);

                    if (isParkourJumpPossible(pos, targetPos)) {
                        jumpPositions.add(targetPos);
                    }
                }
            }
        }

        return jumpPositions;
    }

    /**
     * Determines if a parkour jump is possible between two positions
     * @param start The starting position
     * @param end The ending position
     * @return true if the jump is possible
     */
    private static boolean isParkourJumpPossible(BlockPos start, BlockPos end) {
        // Check if the target is safe to stand on
        if (!isSafeToStandAt(end)) return false;

        // Check if there's enough headroom at the start
        if (!getBlockInfo(start.up(2)).isSolid) return false;

        // Calculate horizontal and vertical distance
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        int dy = end.getY() - start.getY();

        double horizontalDist = Math.sqrt(dx*dx + dz*dz);

        // Check if the jump is within possible range
        if (horizontalDist > 4.0) return false;
        if (dy > 1) return false;  // Can't jump up more than 1 block
        if (dy < -3) return false; // Can't safely fall more than 3 blocks

        // Check if there's a clear path for the jump
        // For simplicity, we'll check a few points along the trajectory
        int steps = (int) (horizontalDist * 2);
        for (int i = 1; i < steps; i++) {
            double progress = i / (double) steps;

            // Calculate position along jump arc
            double jumpHeight = progress * (1 - progress) * 1.2; // Simple parabola

            int x = start.getX() + (int) (dx * progress);
            int y = start.getY() + (int) (dy * progress + jumpHeight);
            int z = start.getZ() + (int) (dz * progress);

            BlockPos pos = new BlockPos(x, y, z);

            // Check if this position is clear
            if (getBlockInfo(pos).isSolid) {
                return false;
            }
        }

        return true;
    }

    /**
     * Class to store information about a block
     */
    public static class BlockInfo {
        public final boolean isSolid;
        public final boolean isWater;
        public final boolean isClimbable;
        public final boolean isDangerous;
        public final boolean isInteractable;
        public final long timestamp;

        public BlockInfo(boolean isSolid, boolean isWater, boolean isClimbable,
                         boolean isDangerous, boolean isInteractable) {
            this.isSolid = isSolid;
            this.isWater = isWater;
            this.isClimbable = isClimbable;
            this.isDangerous = isDangerous;
            this.isInteractable = isInteractable;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Gets a list of dangerous blocks in the analyzed area
     * @return List of dangerous block positions
     */
    public static List<BlockPos> getDangerousBlocks() {
        return new ArrayList<>(dangerousBlocks);
    }

    /**
     * Gets a list of water blocks in the analyzed area
     * @return List of water block positions
     */
    public static List<BlockPos> getWaterBlocks() {
        return new ArrayList<>(waterBlocks);
    }

    /**
     * Gets a list of climbable blocks in the analyzed area
     * @return List of climbable block positions
     */
    public static List<BlockPos> getClimbableBlocks() {
        return new ArrayList<>(climbableBlocks);
    }

    /**
     * Clears the block cache
     */
    public static void clearCache() {
        blockCache.clear();
    }
}