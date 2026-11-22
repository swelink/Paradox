package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.HandShadersModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerItemInHandLayer.class)
public abstract class PlayerItemInHandShadersMixin {
    @WrapOperation(method = "renderItemHeldToEye", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void paradox$captureHeldItem(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector,
                                       int light, int overlay, int outline, Operation<Void> original,
                                       @Local(argsOnly = true) AvatarRenderState state) {
        var module = Paradox.MODULES.get(HandShadersModule.class);
        module.renderer().captureHeldItem(state, item, pose, light, overlay, module);
        original.call(item, pose, collector, light, overlay, outline);
    }
}
