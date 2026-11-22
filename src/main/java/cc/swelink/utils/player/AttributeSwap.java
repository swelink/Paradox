package cc.swelink.utils.player;

import cc.swelink.utils.combat.AttackUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class AttributeSwap {
    private AttributeSwap() {
    }

    public static boolean attackWithSlot(Minecraft client, Entity target, int slot) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null || player.isSpectator()
                || target == null || target == player || !target.isAlive() || target.level() != client.level) {
            return false;
        }
        if (!swap(client, slot)) {
            return false;
        }

        return AttackUtils.attack(client, target);
    }

    public static boolean swap(Minecraft client, int slot) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            return false;
        }
        client.player.getInventory().setSelectedSlot(slot);
        SlotSelection.syncCarriedItem(client);
        return true;
    }

}
