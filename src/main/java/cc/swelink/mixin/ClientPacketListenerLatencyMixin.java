package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.WatermarkModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerLatencyMixin {
    @Inject(method = "handlePongResponse", at = @At("HEAD"))
    private void paradox$measureLatency(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        Paradox.MODULES.get(WatermarkModule.class).onLatencyPong((ClientPacketListener) (Object) this, packet.time());
    }
}
