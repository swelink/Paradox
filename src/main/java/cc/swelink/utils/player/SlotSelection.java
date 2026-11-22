package cc.swelink.utils.player;

import cc.swelink.mixin.MultiPlayerGameModeInvoker;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;

public final class SlotSelection {
    private static Object owner;
    private static Inventory inventory;

    private SlotSelection() {
    }

    public static void resetForInventory(Inventory current) {
        if (inventory != current) {
            owner = null;
            inventory = null;
        }
    }

    public static boolean begin(Object requestedOwner, Inventory requestedInventory) {
        if (requestedOwner == null || requestedInventory == null) {
            return false;
        }
        if (inventory != requestedInventory) {
            owner = null;
            inventory = null;
        }
        if (owner != null && owner != requestedOwner) {
            return false;
        }
        owner = requestedOwner;
        inventory = requestedInventory;
        return true;
    }

    public static boolean owns(Object requestedOwner, Inventory requestedInventory) {
        return owner == requestedOwner && inventory == requestedInventory;
    }

    public static boolean isOwned() {
        return owner != null;
    }

    public static boolean select(Object requestedOwner, Inventory requestedInventory, int slot) {
        if (!owns(requestedOwner, requestedInventory) || !Inventory.isHotbarSlot(slot)) {
            return false;
        }
        requestedInventory.setSelectedSlot(slot);
        return true;
    }

    public static void complete(Object requestedOwner, int restoreSlot) {
        if (owner != requestedOwner) {
            return;
        }
        if (inventory != null && Inventory.isHotbarSlot(restoreSlot)) {
            inventory.setSelectedSlot(restoreSlot);
        }
        owner = null;
        inventory = null;
    }

    public static void syncCarriedItem(Minecraft client) {
        if (client.player != null && client.level != null && client.gameMode != null) {
            ((MultiPlayerGameModeInvoker) client.gameMode).paradox$syncSelectedSlot();
        }
    }
}
