package ac.grim.grimac.platform.minestom.world;

import ac.grim.grimac.platform.api.world.PlatformChunk;
import ac.grim.grimac.platform.api.world.PlatformWorld;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record MinestomPlatformWorld(@Nullable Instance instance) implements PlatformWorld {
    private static final String UNKNOWN_NAME = "unknown";

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return instance != null && instance.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public WrappedBlockState getBlockAt(int x, int y, int z) {
        Chunk chunk = chunk(CoordConversion.globalToChunk(x), CoordConversion.globalToChunk(z));
        return WrappedBlockState.getByGlobalId(MinestomPlatformChunk.stateId(chunk, x, y, z));
    }

    @Override
    public String getName() {
        return instance == null ? UNKNOWN_NAME : instance.getDimensionName();
    }

    @Override
    public @Nullable UUID getUID() {
        return instance == null ? null : instance.getUuid();
    }

    @Override
    public PlatformChunk getChunkAt(int currChunkX, int currChunkZ) {
        return new MinestomPlatformChunk(chunk(currChunkX, currChunkZ));
    }

    @Override
    public boolean isLoaded() {
        return instance != null && instance.isRegistered();
    }

    private @Nullable Chunk chunk(int chunkX, int chunkZ) {
        return instance == null ? null : instance.getChunk(chunkX, chunkZ);
    }
}
