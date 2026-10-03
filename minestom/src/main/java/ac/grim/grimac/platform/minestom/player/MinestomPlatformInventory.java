package ac.grim.grimac.platform.minestom.player;

import ac.grim.grimac.platform.api.player.PlatformInventory;
import ac.grim.grimac.platform.minestom.utils.MinestomConversionUtil;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import lombok.RequiredArgsConstructor;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;

@RequiredArgsConstructor
public final class MinestomPlatformInventory implements PlatformInventory {
    private static final int BOOTS_SLOT = 36;
    private static final int LEGGINGS_SLOT = 37;
    private static final int CHESTPLATE_SLOT = 38;
    private static final int HELMET_SLOT = 39;
    private static final int OFFHAND_SLOT = 40;
    private static final int CONTENTS_SIZE = 41;

    private final Player player;

    @Override
    public ItemStack getItemInHand() {
        return MinestomConversionUtil.fromMinestomItemStack(player.getItemInMainHand());
    }

    @Override
    public ItemStack getItemInOffHand() {
        return MinestomConversionUtil.fromMinestomItemStack(player.getItemInOffHand());
    }

    @Override
    public ItemStack getStack(int bukkitSlot, int vanillaSlot) {
        return MinestomConversionUtil.fromMinestomItemStack(player.getInventory().getItemStack(toMinestomSlot(bukkitSlot)));
    }

    @Override
    public ItemStack getHelmet() {
        return MinestomConversionUtil.fromMinestomItemStack(player.getHelmet());
    }

    @Override
    public ItemStack getChestplate() {
        return MinestomConversionUtil.fromMinestomItemStack(player.getChestplate());
    }

    @Override
    public ItemStack getLeggings() {
        return MinestomConversionUtil.fromMinestomItemStack(player.getLeggings());
    }

    @Override
    public ItemStack getBoots() {
        return MinestomConversionUtil.fromMinestomItemStack(player.getBoots());
    }

    @Override
    public ItemStack[] getContents() {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = new ItemStack[CONTENTS_SIZE];
        for (int slot = 0; slot < CONTENTS_SIZE; slot++) {
            contents[slot] = MinestomConversionUtil.fromMinestomItemStack(inventory.getItemStack(toMinestomSlot(slot)));
        }
        return contents;
    }

    @Override
    public String getOpenInventoryKey() {
        AbstractInventory open = player.getOpenInventory();
        if (!(open instanceof Inventory inventory)) {
            return player.getGameMode() == GameMode.CREATIVE ? "CREATIVE" : "CRAFTING";
        }
        return switch (inventory.getInventoryType()) {
            case CHEST_1_ROW, CHEST_2_ROW, CHEST_3_ROW, CHEST_4_ROW, CHEST_5_ROW, CHEST_6_ROW -> "CHEST";
            case WINDOW_3X3 -> "DISPENSER";
            case CRAFTING -> "WORKBENCH";
            default -> inventory.getInventoryType().name();
        };
    }

    private static int toMinestomSlot(int bukkitSlot) {
        return switch (bukkitSlot) {
            case BOOTS_SLOT -> PlayerInventoryUtils.BOOTS_SLOT;
            case LEGGINGS_SLOT -> PlayerInventoryUtils.LEGGINGS_SLOT;
            case CHESTPLATE_SLOT -> PlayerInventoryUtils.CHESTPLATE_SLOT;
            case HELMET_SLOT -> PlayerInventoryUtils.HELMET_SLOT;
            case OFFHAND_SLOT -> PlayerInventoryUtils.OFFHAND_SLOT;
            default -> bukkitSlot;
        };
    }
}
