package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.AspectRatioModule;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public abstract class CameraAspectRatioMixin {
    @ModifyVariable(method = "setupPerspective", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float paradox$aspectWidth(float width, @Local(argsOnly = true, ordinal = 4) float height) {
        if (((Camera) (Object) this).isPanoramicMode() || height <= 0) return width;
        return Paradox.MODULES.get(AspectRatioModule.class).aspectRatio(width / height) * height;
    }

    @ModifyArg(method = "createProjectionMatrixForCulling", at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix4f;perspective(FFFFZ)Lorg/joml/Matrix4f;"), index = 1)
    private float paradox$cullingAspect(float original) {
        return ((Camera) (Object) this).isPanoramicMode() ? original
                : Paradox.MODULES.get(AspectRatioModule.class).aspectRatio(original);
    }
}
