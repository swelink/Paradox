package cc.swelink.utils.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.Util;

public final class LatencyTracker {
    private static final long INTERVAL = 1_000_000_000L;
    private static final long TIMEOUT = 3_000_000_000L;
    private static final long STALE = 5_000_000_000L;
    private ClientPacketListener connection;
    private long nextProbe;
    private long started;
    private long token;
    private long sampled;
    private long milliseconds = -1;
    private boolean pending;

    public synchronized void update(Minecraft client, boolean enabled) {
        var current = client.getConnection();
        if (!enabled || current == null || client.player == null || client.level == null
                || !current.getConnection().isConnected()) {
            reset();
            return;
        }
        if (connection != current) {
            reset();
            connection = current;
        }
        if (client.hasSingleplayerServer()) {
            milliseconds = 0;
            sampled = System.nanoTime();
            return;
        }
        long now = System.nanoTime();
        if (pending && now - started >= TIMEOUT) {
            pending = false;
            milliseconds = -1;
        }
        if (pending || now < nextProbe) return;
        started = now;
        token = Util.getMillis();
        pending = true;
        nextProbe = now + INTERVAL;
        current.send(new ServerboundPingRequestPacket(token));
    }

    public synchronized void onPong(ClientPacketListener source, long value) {
        if (source != connection || !pending || value != token) return;
        long now = System.nanoTime();
        long elapsed = now - started;
        pending = false;
        if (elapsed < 0 || elapsed >= TIMEOUT) return;
        milliseconds = Math.round(elapsed / 1_000_000.0);
        sampled = now;
    }

    public synchronized long milliseconds(Minecraft client) {
        var current = client.getConnection();
        if (current == null || client.player == null) return -1;
        if (client.hasSingleplayerServer()) return 0;
        if (connection == current && milliseconds >= 0 && System.nanoTime() - sampled < STALE) {
            return milliseconds;
        }
        var info = current.getPlayerInfo(client.player.getUUID());
        return info == null || info.getLatency() < 0 ? -1 : info.getLatency();
    }

    public synchronized void reset() {
        connection = null;
        nextProbe = 0;
        started = 0;
        token = 0;
        sampled = 0;
        milliseconds = -1;
        pending = false;
    }
}
