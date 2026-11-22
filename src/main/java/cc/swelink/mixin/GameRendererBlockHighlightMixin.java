package cc.swelink.mixin;

import cc.swelink.systems.modules.render.PostProcessingModule;
import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.BlockHighlightModule;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererBlockHighlightMixin {
    @Shadow @Final private RenderTarget mainRenderTarget;
    @Shadow @Final private CrossFrameResourcePool resourcePool;
    @Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
            shift = At.Shift.AFTER))
    private void paradox$blockHighlight(CallbackInfo ci, @Local CameraRenderState camera) {
        if (!PostProcessingModule.active()) return;
        Paradox.MODULES.get(BlockHighlightModule.class).render(camera, featureRenderDispatcher, mainRenderTarget, resourcePool);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void paradox$closeBlockHighlight(CallbackInfo ci) {
        Paradox.MODULES.get(BlockHighlightModule.class).close();
    }
}
