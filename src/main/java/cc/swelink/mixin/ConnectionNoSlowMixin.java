package cc.swelink.mixin;

import cc.swelink.utils.network.GameConnection;
import cc.swelink.Paradox;
import cc.swelink.systems.modules.movement.NoSlowModule;
import cc.swelink.utils.combat.ShieldStates;
import cc.swelink.utils.player.ServerMoves;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionNoSlowMixin {
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void paradox$arrived(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (!paradox$game()) return;
        Paradox.MODULES.get(NoSlowModule.class).tracker().arrived(packet);
        ServerMoves.INSTANCE.arrived(packet);
        ShieldStates.INSTANCE.arrived(packet);
    }

    @Inject(method = "doSendPacket", at = @At("HEAD"))
    private void paradox$sent(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (!paradox$game()) return;
        Paradox.MODULES.get(NoSlowModule.class).tracker().sent(packet);
        ServerMoves.INSTANCE.sent(packet);
    }

    @Unique
    private boolean paradox$game() {
        return GameConnection.active((Connection) (Object) this);
    }
}
