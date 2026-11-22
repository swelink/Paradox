package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.FakeLagModule;
import cc.swelink.utils.network.PostMonitor;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionCombatLagMixin {
    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void paradox$delayMovement(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (Paradox.MODULES.get(FakeLagModule.class).outgoing((Connection) (Object) this, packet, listener, flush)) ci.cancel();
        else if (((Connection) (Object) this).getSending() == PacketFlow.SERVERBOUND) PostMonitor.sent(packet);
    }
}
