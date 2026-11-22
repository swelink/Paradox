package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.FreelookModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class CameraFreelookMixin {
    @ModifyExpressionValue(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float paradox$freelookYaw(float original) {
        var module = Paradox.MODULES.get(FreelookModule.class);
        return module.active() ? module.yaw() : original;
    }

    @ModifyExpressionValue(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float paradox$freelookPitch(float original) {
        var module = Paradox.MODULES.get(FreelookModule.class);
        return module.active() ? module.pitch() : original;
    }
}
