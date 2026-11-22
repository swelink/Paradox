package cc.swelink.mixin;

import cc.swelink.Paradox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerRotationMixin {
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void paradox$positionCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        paradox$acceptCorrection();
    }

    @Inject(method = "handleRotatePlayer", at = @At("TAIL"))
    private void paradox$rotationCorrection(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        paradox$acceptCorrection();
    }

    @Unique
    private static void paradox$acceptCorrection() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            Paradox.ROTATIONS.acceptServerRotation(player.getYRot(), player.getXRot());
        }
    }
}
