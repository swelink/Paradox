package cc.swelink.mixin;

import cc.swelink.utils.network.GameConnection;
import cc.swelink.Paradox;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.systems.modules.combat.FakeLagModule;
import cc.swelink.systems.modules.movement.ElytraBoostModule;
import cc.swelink.systems.modules.movement.speed.SpeedModule;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.channel.ChannelFutureListener;

@Mixin(Connection.class)
public abstract class ConnectionRotationMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("RETURN"))
    private void paradox$recordRotation(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (!GameConnection.activeOnClientThread((Connection) (Object) this)) {
            return;
        }
        Paradox.MODULES.get(ElytraBoostModule.class).sent(packet);
        if (packet instanceof ServerboundMovePlayerPacket || packet instanceof ServerboundClientTickEndPacket) {
            Paradox.EVENTS.post(new TickBoundaryEvent());
        }
        if (packet instanceof ServerboundMovePlayerPacket movement && movement.hasPosition()
                && !Paradox.MODULES.get(FakeLagModule.class).isQueued(packet)) {
            Paradox.MODULES.get(SpeedModule.class).sentPosition();
        }
        if (packet instanceof ServerboundMovePlayerPacket) Paradox.ROTATIONS.movementSent();
        if (packet instanceof ServerboundClientTickEndPacket) Paradox.ROTATIONS.tickEndSent();
        if (packet instanceof ServerboundMovePlayerPacket movement && movement.hasRotation()) {
            Paradox.ROTATIONS.recordMovementPacket(movement.getYRot(0), movement.getXRot(0));
        }
    }
}
