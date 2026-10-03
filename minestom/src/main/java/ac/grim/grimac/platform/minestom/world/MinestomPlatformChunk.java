package ac.grim.grimac.platform.minestom.world;

import ac.grim.grimac.platform.api.world.PlatformChunk;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

public record MinestomPlatformChunk(@Nullable Chunk chunk) implements PlatformChunk {
    @Override
    public int getBlockID(int x, int y, int z) {
        return stateId(chunk, x, y, z);
    }

    static int stateId(@Nullable Chunk chunk, int x, int y, int z) {
        if (chunk == null) return Block.AIR.stateId();
        chunk.lockReadLock();
        try {
            Block block = chunk.getBlock(x, y, z, Block.Getter.Condition.TYPE);
            return block == null ? Block.AIR.stateId() : block.stateId();
        } finally {
            chunk.unlockReadLock();
        }
    }
}
