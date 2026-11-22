package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.SwingAnimationsModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonSwingAnimationsMixin {
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
            ordinal = 4))
    private void paradox$swipePose(FirstPersonHandsAndItemsRenderer renderer, PoseStack pose, HumanoidArm arm,
                                float equipProgress, Operation<Void> original,
                                @Local(argsOnly = true, ordinal = 2) float swingProgress,
                                @Local(argsOnly = true) InteractionHand hand,
                                @Local(argsOnly = true) ItemStack item) {
        var module = Paradox.MODULES.get(SwingAnimationsModule.class);
        if (module.customizes(hand, item)) {
            module.transform(pose, arm, swingProgress, equipProgress);
        } else {
            original.call(renderer, pose, arm, equipProgress);
        }
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V",
            ordinal = 2))
    private void paradox$swipeSwing(FirstPersonHandsAndItemsRenderer renderer, float progress, PoseStack pose,
                                 int direction, HumanoidArm arm, Operation<Void> original,
                                 @Local(argsOnly = true) InteractionHand hand,
                                 @Local(argsOnly = true) ItemStack item) {
        if (!Paradox.MODULES.get(SwingAnimationsModule.class).customizes(hand, item)) {
            original.call(renderer, progress, pose, direction, arm);
        }
    }
}
