package ac.grim.grimac.platform.minestom.permission;

import net.minestom.server.command.CommandSender;

@FunctionalInterface
public interface PermissionProvider {
    PermissionProvider DEFAULTS = (sender, permission, fallback) -> fallback;

    boolean hasPermission(CommandSender sender, String permission, boolean fallback);
}
