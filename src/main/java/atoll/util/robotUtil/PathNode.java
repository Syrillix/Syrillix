package atoll.util.robotUtil;

import net.minecraft.util.BlockPos;

/**
 * Represents a node in the A* pathfinding algorithm
 */
public class PathNode {
    BlockPos pos;
    PathNode parent;
    double gCost = Double.MAX_VALUE; // Cost from start to this node
    double hCost = 0; // Heuristic cost from this node to goal
    double fCost = Double.MAX_VALUE; // Total cost (g + h)

    public PathNode(BlockPos pos) {
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
