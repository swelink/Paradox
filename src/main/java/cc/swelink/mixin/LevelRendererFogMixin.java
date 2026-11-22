package cc.swelink.mixin;

import cc.swelink.utils.render.FogPass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererFogMixin {
    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;hasAnySeeThrough()Z"))
    private void paradox$fogBeforeOverlays(CallbackInfo ci, @Local RenderTarget target) {
        FogPass.apply(target);
        FogPass.drawDeferred(target);
    }

    @WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void paradox$splitForAmbience(LevelRenderer renderer, ChunkSectionsToRender sections,
                                          FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass,
                                          Operation<Void> original, @Local RenderTarget target) {
        if (!FogPass.wantsAmbience()) {
            original.call(renderer, sections, frame, pass);
            return;
        }
        try {
            original.call(renderer, sections, frame, FogPass.beginSplit(pass, target));
        } finally {
            FogPass.endSplit(pass);
        }
    }

    @WrapOperation(method = "executeClassicTransparency", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucent(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void paradox$ambienceBeforeTranslucentTerrain(FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass,
                                                          Operation<Void> original) {
        original.call(frame, pass);
        FogPass.splitIfPending();
    }
}
