package cc.swelink.mixin;

import cc.swelink.utils.render.FogPass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class PreparedFrameAmbienceMixin {
    @WrapOperation(method = "executeTranslucent", at = @At(value = "INVOKE", ordinal = 2, target =
            "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executePhase(Lnet/minecraft/client/renderer/feature/phase/FeatureRenderPhase;Lnet/minecraft/client/renderer/feature/FeatureFrameContext;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void paradox$ambienceBeforeNameTags(FeatureRenderDispatcher.PreparedFrame frame, FeatureRenderPhase<?> phase,
                                                FeatureFrameContext context, RenderPass pass, Operation<Void> original) {
        FogPass.splitIfPending();
        original.call(frame, phase, context, pass);
    }

    @WrapOperation(method = "executeTranslucent", at = @At(value = "INVOKE", ordinal = 4, target =
            "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executePhase(Lnet/minecraft/client/renderer/feature/phase/FeatureRenderPhase;Lnet/minecraft/client/renderer/feature/FeatureFrameContext;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void paradox$customGeometryAfterFog(FeatureRenderDispatcher.PreparedFrame frame, FeatureRenderPhase<?> phase,
                                                FeatureFrameContext context, RenderPass pass, Operation<Void> original) {
        if (!FogPass.deferCustomGeometry(frame, phase, context)) original.call(frame, phase, context, pass);
    }
}
