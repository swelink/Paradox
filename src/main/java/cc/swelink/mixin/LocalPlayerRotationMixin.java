package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.movement.OmnisprintModule;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerRotationMixin {
    @ModifyExpressionValue(
            method = {"canStartSprinting", "shouldStopRunSprinting", "shouldStopSwimSprinting"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z")
    )
    private boolean paradox$sprintDirection(boolean original) {
        return original || Paradox.MODULES.get(OmnisprintModule.class).allowsAnyDirection(Minecraft.getInstance());
    }

    @ModifyExpressionValue(
            method = {"sendPosition", "sendChanges"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F")
    )
    private float paradox$packetYaw(float original) {
        return Paradox.ROTATIONS.packetYaw(original);
    }

    @ModifyExpressionValue(
            method = {"sendPosition", "sendChanges"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F")
    )
    private float paradox$packetPitch(float original) {
        return Paradox.ROTATIONS.packetPitch(original);
    }

}
