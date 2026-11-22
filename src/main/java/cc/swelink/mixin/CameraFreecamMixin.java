package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.FreecamModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraFreecamMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;calculateFov(F)F"))
    private void paradox$freecam(DeltaTracker tracker, CallbackInfo ci) {
        var module = Paradox.MODULES.get(FreecamModule.class);
        if (!module.active()) return;
        var camera = (Camera) (Object) this;
        setRotation(module.yaw(), module.pitch());
        setPosition(module.position(camera.getCameraEntityPartialTicks(tracker)));
        detached = true;
    }
}
