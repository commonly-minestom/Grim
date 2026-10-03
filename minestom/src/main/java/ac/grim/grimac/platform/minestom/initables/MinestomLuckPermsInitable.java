package ac.grim.grimac.platform.minestom.initables;

import ac.grim.grimac.manager.init.OptionalReflectiveInitable;
import ac.grim.grimac.utils.reflection.ReflectionUtils;

public final class MinestomLuckPermsInitable extends OptionalReflectiveInitable {
    public static final String PROVIDER_CLASS = "net.luckperms.api.LuckPermsProvider";
    private static final String HANDLER_CLASS = "ac.grim.grimac.platform.minestom.initables.MinestomLuckPermsHandler";

    public MinestomLuckPermsInitable() {
        super(HANDLER_CLASS, "Error when initializing LuckPerms hook");
    }

    @Override
    protected boolean isAvailable() {
        return ReflectionUtils.hasClass(PROVIDER_CLASS);
    }
}
