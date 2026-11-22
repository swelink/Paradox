package cc.swelink.utils.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

public final class InventoryUtils {
    private InventoryUtils() {
    }

    public static int findHotbar(Inventory inventory, Item item) {
        return findHotbar(inventory, stack -> stack.is(item));
    }

    public static int findHotbar(Inventory inventory, Predicate<ItemStack> matches) {
        return find(inventory, Inventory.getSelectionSize(), matches);
    }

    public static int findInventory(Inventory inventory, Item item) {
        return findInventory(inventory, stack -> stack.is(item));
    }

    public static int findInventory(Inventory inventory, Predicate<ItemStack> matches) {
        return find(inventory, inventory.getNonEquipmentItems().size(), matches);
    }

    private static int find(Inventory inventory, int size, Predicate<ItemStack> matches) {
        for (int slot = 0; slot < size; slot++) {
            if (matches.test(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    public static boolean swapInventoryItem(Minecraft client, int inventorySlot, int hotbarSlot) {
        if (client.player == null || client.gameMode == null) {
            return false;
        }
        var menu = client.player.containerMenu;
        for (int index = 0; index < menu.slots.size(); index++) {
            var slot = menu.slots.get(index);
            if (slot.container == client.player.getInventory() && slot.getContainerSlot() == inventorySlot) {
                client.gameMode.handleContainerInput(menu.containerId, index, hotbarSlot, ContainerInput.SWAP, client.player);
                return true;
            }
        }
        return false;
    }

    public static boolean swapPlayerInventoryItem(Minecraft client, int inventorySlot, int hotbarSlot) {
        return client.player != null && client.player.containerMenu == client.player.inventoryMenu
                && swapInventoryItem(client, inventorySlot, hotbarSlot);
    }
}
