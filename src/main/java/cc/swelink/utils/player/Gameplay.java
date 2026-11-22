package cc.swelink.utils.player;

import net.minecraft.client.Minecraft;

public final class Gameplay {
    private Gameplay() {
    }

    public static boolean focused(Minecraft client) {
        return client.gui.screen() == null && client.getWindow().isFocused();
    }

    public static boolean canAct(Minecraft client) {
        var player = client.player;
        return player != null && client.level != null && player.isAlive() && !player.isSpectator() && focused(client);
    }
}
