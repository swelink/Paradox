package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.utils.rotation.Rotation;

import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerboundUseItemPacket.class)
public class ServerboundUseItemPacketRotationMixin {
    @Mutable
    @Shadow
    @Final
    private float yRot;
    @Mutable
    @Shadow
    @Final
    private float xRot;

    @Inject(method = "<init>(Lnet/minecraft/world/InteractionHand;IFF)V", at = @At("RETURN"))
    private void paradox$applyManagedRotation(InteractionHand hand, int sequence, float yaw, float pitch, CallbackInfo callbackInfo) {
        yRot = Paradox.ROTATIONS.packetYaw(yaw);
        xRot = Paradox.ROTATIONS.packetPitch(pitch);
        Paradox.ROTATIONS.lockUseRotation(new Rotation(yRot, xRot));
    }
}
