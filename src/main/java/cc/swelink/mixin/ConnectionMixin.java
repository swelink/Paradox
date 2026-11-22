package cc.swelink.mixin;

import cc.swelink.utils.network.GameConnection;
import cc.swelink.Paradox;
import cc.swelink.events.AttackEvent;
import cc.swelink.events.PacketSentEvent;
import cc.swelink.events.PostAttackEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import com.mojang.logging.LogUtils;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "exceptionCaught", at = @At("HEAD"))
    private void paradox$logException(ChannelHandlerContext context, Throwable cause, CallbackInfo ci) {
        LogUtils.getLogger().error("Connection exception", cause);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
    private void paradox$packetSent(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (GameConnection.activeOnClientThread((Connection) (Object) this)) {
            Paradox.EVENTS.post(new PacketSentEvent(client, packet));
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
    private void paradox$beforeAttack(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (!(packet instanceof ServerboundAttackPacket attack)) {
            return;
        }
        var client = Minecraft.getInstance();
        if (!GameConnection.activeOnClientThread((Connection) (Object) this)) {
            return;
        }

        Paradox.EVENTS.post(new AttackEvent(client, attack.entityId()));
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("RETURN"))
    private void paradox$afterCriticalAttack(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (!(packet instanceof ServerboundAttackPacket)) {
            return;
        }
        var client = Minecraft.getInstance();
        if (GameConnection.activeOnClientThread((Connection) (Object) this)) {
            Paradox.EVENTS.post(new PostAttackEvent(client));
        }
    }
}
