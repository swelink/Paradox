package cc.swelink.events;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;

public final class IncomingPacketEvent extends CancellableEvent {
    private final ClientPacketListener source;
    private final Packet<?> packet;

    public IncomingPacketEvent(ClientPacketListener source, Packet<?> packet) {
        this.source = source;
        this.packet = packet;
    }

    public ClientPacketListener source() {
        return source;
    }

    public Packet<?> packet() {
        return packet;
    }
}
