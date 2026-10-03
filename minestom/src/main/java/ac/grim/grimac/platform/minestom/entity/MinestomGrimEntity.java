package ac.grim.grimac.platform.minestom.entity;

import ac.grim.grimac.platform.api.entity.GrimEntity;
import ac.grim.grimac.platform.api.world.PlatformWorld;
import ac.grim.grimac.platform.minestom.utils.MinestomConversionUtil;
import ac.grim.grimac.platform.minestom.world.MinestomPlatformWorld;
import ac.grim.grimac.utils.math.Location;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MinestomGrimEntity implements GrimEntity {
    private final Entity entity;
    private MinestomPlatformWorld world;

    public MinestomGrimEntity(Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    @Override
    public UUID getUniqueId() {
        return entity.getUuid();
    }

    @Override
    public boolean eject() {
        List<Entity> passengers = List.copyOf(entity.getPassengers());
        for (Entity passenger : passengers) {
            entity.removePassenger(passenger);
        }
        return !passengers.isEmpty();
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location) {
        Pos position = MinestomConversionUtil.toMinestomPos(location);
        Instance target = location.getWorld() instanceof MinestomPlatformWorld platformWorld ? platformWorld.instance() : null;
        CompletableFuture<Void> teleport = target == null || target == entity.getInstance()
                ? entity.teleport(position)
                : entity.setInstance(target, position);
        return teleport.handle((ignored, throwable) -> throwable == null);
    }

    @Override
    public @NotNull Entity getNative() {
        return entity;
    }

    @Override
    public boolean isDead() {
        return entity.isRemoved() || entity instanceof LivingEntity living && living.isDead();
    }

    @Override
    public PlatformWorld getWorld() {
        Instance instance = entity.getInstance();
        MinestomPlatformWorld world = this.world;
        if (world == null || world.instance() != instance) {
            world = new MinestomPlatformWorld(instance);
            this.world = world;
        }
        return world;
    }

    @Override
    public Location getLocation() {
        Pos position = entity.getPosition();
        return new Location(getWorld(), position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }

    @Override
    public double distanceSquared(double x, double y, double z) {
        Pos position = entity.getPosition();
        double distanceX = position.x() - x;
        double distanceY = position.y() - y;
        double distanceZ = position.z() - z;
        return distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ;
    }
}
