package cc.swelink.events;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

public record PacketSentEvent(Minecraft client, Packet<?> packet) {
}
