package ac.grim.grimac.platform.minestom.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

public final class LuckPermsPermissionProvider implements PermissionProvider {
    private volatile LuckPerms luckPerms;

    @Override
    public boolean hasPermission(CommandSender sender, String permission, boolean fallback) {
        if (!(sender instanceof Player player)) return fallback;
        LuckPerms luckPerms = luckPerms();
        if (luckPerms == null) return fallback;
        User user = luckPerms.getUserManager().getUser(player.getUuid());
        if (user == null) return fallback;
        return switch (user.getCachedData().getPermissionData().checkPermission(permission)) {
            case TRUE -> true;
            case FALSE -> false;
            case UNDEFINED -> fallback;
        };
    }

    private LuckPerms luckPerms() {
        LuckPerms luckPerms = this.luckPerms;
        if (luckPerms != null) return luckPerms;
        try {
            luckPerms = LuckPermsProvider.get();
            this.luckPerms = luckPerms;
            return luckPerms;
        } catch (IllegalStateException exception) {
            return null;
        }
    }
}
