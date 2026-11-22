package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.SmoothCameraModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraSmoothMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void paradox$smoothCameraPosition(DeltaTracker tracker, CallbackInfo ci) {
        var camera = (Camera) (Object) this;
        setPosition(Paradox.MODULES.get(SmoothCameraModule.class).smooth(camera, camera.position()));
    }
}
