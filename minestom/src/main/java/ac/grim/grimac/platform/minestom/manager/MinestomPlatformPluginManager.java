package ac.grim.grimac.platform.minestom.manager;

import ac.grim.grimac.platform.api.PlatformPlugin;
import ac.grim.grimac.platform.api.manager.PlatformPluginManager;
import org.jetbrains.annotations.Nullable;

public final class MinestomPlatformPluginManager implements PlatformPluginManager {
    private static final PlatformPlugin[] NO_PLUGINS = new PlatformPlugin[0];

    @Override
    public PlatformPlugin[] getPlugins() {
        return NO_PLUGINS;
    }

    @Override
    public @Nullable PlatformPlugin getPlugin(String pluginName) {
        return null;
    }
}
