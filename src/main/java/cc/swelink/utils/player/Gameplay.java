package cc.swelink.utils.player;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.AutoTotemModule;

import net.minecraft.client.Minecraft;

public final class Gameplay {
    private Gameplay() {
    }

    public static boolean focused(Minecraft client) {
        return !inMenu(client) && client.getWindow().isFocused();
    }

    public static boolean inMenu(Minecraft client) {
        return client.gui.screen() != null || Paradox.MODULES.get(AutoTotemModule.class).silentBusy();
    }

    public static boolean canAct(Minecraft client) {
        var player = client.player;
        return player != null && client.level != null && player.isAlive() && !player.isSpectator() && focused(client);
    }
}
