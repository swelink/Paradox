package cc.swelink.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.rendertype.RenderType;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionOitMixin {
    @Shadow @Final public SimpleFeatureRenderPhase alwaysOnTopGizmos;
    @Shadow @Final public SimpleFeatureRenderPhase oitTranslucent;
    @Shadow @Final public SimpleFeatureRenderPhase translucentCustomGeometry;

    @Inject(method = "submitCustomGeometry", at = @At("HEAD"), cancellable = true)
    private void paradox$skipOit(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer,
            CallbackInfo ci) {
        if (translucentCustomGeometry != oitTranslucent || type.isOutline() || !type.hasBlending()) return;
        if (!((RenderTypeAccessor) type).paradox$getName().startsWith("paradox_")) return;
        alwaysOnTopGizmos.submit(new CustomFeatureRenderer.Submit(pose.last().copy(), type, renderer));
        ci.cancel();
    }
}
