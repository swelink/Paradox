package cc.swelink.mixin;

import cc.swelink.renderer.CapeCloth;
import cc.swelink.renderer.CapeTexture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CapeLayer.class)
public abstract class CapeLayerGlowMixin {
    @WrapOperation(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V"))
    private void paradox$glow(SubmitNodeCollector collector, Model<?> model, Object renderState, PoseStack pose, RenderType type,
                            int light, int overlay, int outline, Operation<Void> original,
                            @Local(argsOnly = true) AvatarRenderState state) {
        boolean glowing = CapeTexture.owns(state.skin.cape().texturePath());
        int shownLight = glowing ? CapeTexture.FULL_BRIGHT : light;
        if (CapeCloth.submit(collector, model, state, pose, type, shownLight, overlay)) return;
        original.call(collector, model, renderState, pose, type, shownLight, overlay, outline);
    }
}
