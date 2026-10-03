package ac.grim.grimac.platform.minestom;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.platform.minestom.initables.MinestomLuckPermsInitable;
import ac.grim.grimac.platform.minestom.permission.LuckPermsPermissionProvider;
import ac.grim.grimac.platform.minestom.permission.PermissionProvider;
import ac.grim.grimac.utils.reflection.ReflectionUtils;
import net.minestom.server.MinecraftServer;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GrimMinestom {
    private static final AtomicBoolean ENABLED = new AtomicBoolean();

    private GrimMinestom() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static void enable() {
        builder().enable();
    }

    public static final class Builder {
        private Path dataFolder = Path.of("GrimAC");
        private PermissionProvider permissions;

        private Builder() {
        }

        public Builder dataFolder(Path dataFolder) {
            this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
            return this;
        }

        public Builder permissions(PermissionProvider permissions) {
            this.permissions = Objects.requireNonNull(permissions, "permissions");
            return this;
        }

        public void enable() {
            if (MinecraftServer.process() == null) {
                throw new IllegalStateException("MinecraftServer.init() must be called before enabling Grim");
            }
            if (MinecraftServer.isStarted()) {
                throw new IllegalStateException("Grim must be enabled before the server starts");
            }
            if (!ENABLED.compareAndSet(false, true)) {
                throw new IllegalStateException("Grim is already enabled");
            }
            GrimACMinestomLoader loader = new GrimACMinestomLoader(dataFolder, permissions());
            GrimAPI.INSTANCE.load(loader, loader.initables());
            GrimAPI.INSTANCE.start();
            MinecraftServer.getSchedulerManager().buildShutdownTask(GrimAPI.INSTANCE::stop);
        }

        private PermissionProvider permissions() {
            if (permissions != null) return permissions;
            if (ReflectionUtils.hasClass(MinestomLuckPermsInitable.PROVIDER_CLASS)) return new LuckPermsPermissionProvider();
            return PermissionProvider.DEFAULTS;
        }
    }
}
