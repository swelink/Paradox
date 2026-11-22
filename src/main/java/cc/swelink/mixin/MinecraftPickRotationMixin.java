package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.utils.rotation.RotationRaycast;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftPickRotationMixin {
    @Shadow
    public HitResult hitResult;
    @Shadow
    public Entity crosshairPickEntity;

    @Inject(method = "pick", at = @At("TAIL"))
    private void paradox$silentPick(float tickDelta, CallbackInfo ci) {
        var client = (Minecraft) (Object) this;
        if (client.player == null || client.level == null || client.gameMode == null) {
            return;
        }
        if (Paradox.ROTATIONS.isSilent()) {
            var rotation = RotationRaycast.interactionRotation(client, tickDelta);
            var result = RotationRaycast.traceHeldItem(rotation, tickDelta);
            if (result != null) {
                hitResult = result;
                crosshairPickEntity = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
            }
        }
    }
}
