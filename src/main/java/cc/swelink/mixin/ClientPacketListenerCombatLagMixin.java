package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.events.IncomingPacketEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class ClientPacketListenerCombatLagMixin {
    @Redirect(method = "handle", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private void paradox$combatPackets(Packet<?> packet, PacketListener listener) {
        paradox$handle(packet, listener);
    }

    @SuppressWarnings("unchecked")
    @Unique
    private void paradox$handle(Packet<?> packet, PacketListener listener) {
        if (listener instanceof ClientPacketListener source && Minecraft.getInstance().isSameThread()) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                for (var child : bundle.subPackets()) paradox$handle(child, listener);
                return;
            }
            if (Paradox.EVENTS.post(new IncomingPacketEvent(source, packet)).cancelled()) return;
        }
        ((Packet<PacketListener>) packet).handle(listener);
    }
}
