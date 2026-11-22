package cc.swelink.utils.network;

import cc.swelink.utils.time.GameTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;

public final class IncomingPacketDelay {
    private final TimedPacketQueue<Packet<ClientPacketListener>> packets = new TimedPacketQueue<>(16384);
    private final Runnable reset;
    private final Runnable released;
    private ClientPacketListener listener;
    private ClientLevel level;
    private boolean replaying;

    public IncomingPacketDelay(Runnable reset, Runnable released) {
        this.reset = reset;
        this.released = released;
    }

    public IncomingPacketDelay() {
        this(() -> { }, () -> { });
    }

    public boolean accepts(Minecraft client, ClientPacketListener source) {
        return !replaying && source == client.getConnection() && synchronize(client);
    }

    public boolean synchronize(Minecraft client) {
        if (listener != client.getConnection() || level != client.level) {
            if (listener != null && listener == client.getConnection()) flush();
            else packets.clear();
            listener = client.getConnection();
            level = client.level;
            reset.run();
        }
        if (listener == null || !listener.getConnection().isConnected()) {
            packets.clear();
            reset.run();
            return false;
        }
        if (client.player == null || level == null) {
            flush();
            reset.run();
            return false;
        }
        return true;
    }

    public boolean matches(Minecraft client) {
        return client.level == level && client.getConnection() == listener;
    }

    public ClientLevel level() {
        return level;
    }

    public boolean isEmpty() {
        return packets.isEmpty();
    }

    public long age(long now) {
        return packets.age(now);
    }

    @SuppressWarnings("unchecked")
    public boolean offer(Packet<?> packet) {
        return packets.offer((Packet<ClientPacketListener>) packet, GameTime.nanos());
    }

    public void disconnect() {
        packets.clear();
        reset.run();
    }

    public void flush() {
        if (packets.isEmpty()) return;
        if (!connected()) {
            packets.clear();
            return;
        }
        replay(() -> packets.drain(this::deliver));
        released.run();
    }

    public void release(long delay) {
        if (!connected()) {
            packets.clear();
            return;
        }
        replay(() -> packets.release(GameTime.nanos(), delay, this::deliver));
    }

    public void release(long delay, java.util.function.Predicate<Packet<?>> early, java.util.function.Consumer<Packet<?>> released) {
        if (!connected()) {
            packets.clear();
            return;
        }
        replay(() -> packets.release(GameTime.nanos(), delay, early::test, packet -> {
            deliver(packet);
            released.accept(packet);
        }));
    }

    private boolean connected() {
        return listener != null && listener == Minecraft.getInstance().getConnection() && listener.getConnection().isConnected();
    }

    private void replay(Runnable action) {
        replaying = true;
        try {
            action.run();
        } finally {
            replaying = false;
        }
    }

    private void deliver(Packet<ClientPacketListener> packet) {
        if (!listener.shouldHandleMessage(packet)) return;
        try {
            packet.handle(listener);
        } catch (Exception exception) {
            listener.onPacketError(packet, exception);
        }
    }
}
