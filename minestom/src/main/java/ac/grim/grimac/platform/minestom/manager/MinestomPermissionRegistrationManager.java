package ac.grim.grimac.platform.minestom.manager;

import ac.grim.grimac.platform.api.manager.PermissionRegistrationManager;
import ac.grim.grimac.platform.api.permissions.PermissionDefaultValue;
import ac.grim.grimac.platform.minestom.permission.PermissionProvider;
import lombok.RequiredArgsConstructor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class MinestomPermissionRegistrationManager implements PermissionRegistrationManager {
    private static final int OPERATOR_PERMISSION_LEVEL = 4;

    private final Map<String, PermissionDefaultValue> defaults = new ConcurrentHashMap<>(Map.ofEntries(
            Map.entry("grim.alerts", PermissionDefaultValue.OP),
            Map.entry("grim.alerts.enable-on-join", PermissionDefaultValue.OP),
            Map.entry("grim.performance", PermissionDefaultValue.OP),
            Map.entry("grim.profile", PermissionDefaultValue.OP),
            Map.entry("grim.brand", PermissionDefaultValue.OP),
            Map.entry("grim.brand.enable-on-join", PermissionDefaultValue.OP),
            Map.entry("grim.sendalert", PermissionDefaultValue.OP),
            Map.entry("grim.nosetback", PermissionDefaultValue.FALSE),
            Map.entry("grim.nomodifypacket", PermissionDefaultValue.FALSE),
            Map.entry("grim.disabled", PermissionDefaultValue.FALSE),
            Map.entry("grim.exempt", PermissionDefaultValue.FALSE),
            Map.entry("grim.verbose", PermissionDefaultValue.OP),
            Map.entry("grim.verbose.enable-on-join", PermissionDefaultValue.FALSE),
            Map.entry("grim.list", PermissionDefaultValue.FALSE)
    ));
    private final PermissionProvider provider;

    @Override
    public void registerPermission(String name, PermissionDefaultValue defaultValue) {
        defaults.put(name, defaultValue);
    }

    public boolean hasPermission(CommandSender sender, String permission) {
        PermissionDefaultValue defaultValue = defaults.getOrDefault(permission, PermissionDefaultValue.OP);
        return provider.hasPermission(sender, permission, resolve(defaultValue, sender));
    }

    public boolean hasPermission(CommandSender sender, String permission, boolean defaultIfUnset) {
        PermissionDefaultValue defaultValue = defaults.get(permission);
        return provider.hasPermission(sender, permission, defaultValue == null ? defaultIfUnset : resolve(defaultValue, sender));
    }

    private static boolean resolve(PermissionDefaultValue defaultValue, CommandSender sender) {
        return switch (defaultValue) {
            case TRUE -> true;
            case FALSE -> false;
            case OP -> isOperator(sender);
            case NOT_OP -> !isOperator(sender);
        };
    }

    private static boolean isOperator(CommandSender sender) {
        return !(sender instanceof Player player) || player.getPermissionLevel() >= OPERATOR_PERMISSION_LEVEL;
    }
}
