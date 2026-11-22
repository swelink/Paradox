package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.FakePlayerModule;

import io.netty.channel.ChannelFutureListener;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionFakePlayerMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void paradox$fakePlayer(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        var module = Paradox.MODULES.get(FakePlayerModule.class);
        if (packet instanceof ServerboundAttackPacket attack && module.isFake(attack.entityId())) {
            var client = Minecraft.getInstance();
            if (client.isSameThread()) module.hit(client);
            ci.cancel();
        } else if (packet instanceof ServerboundInteractPacket interact && module.isFake(interact.entityId())) {
            ci.cancel();
        }
    }
}
