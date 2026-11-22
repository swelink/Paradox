package cc.swelink.utils.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

public final class GameConnection {
    private GameConnection() {
    }

    public static boolean active(Connection connection) {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && listener.getConnection() == connection;
    }

    public static boolean activeOnClientThread(Connection connection) {
        return Minecraft.getInstance().isSameThread() && active(connection);
    }
}
