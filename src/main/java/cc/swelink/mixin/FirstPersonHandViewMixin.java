package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.HandViewModule;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandViewMixin {
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void paradox$handView(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose,
                                  @Local(argsOnly = true) InteractionHand hand) {
        var player = Minecraft.getInstance().player;
        HumanoidArm main = player == null ? HumanoidArm.RIGHT : player.getMainArm();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? main : main.getOpposite();
        Paradox.MODULES.get(HandViewModule.class).transform(pose, arm == HumanoidArm.LEFT);
    }
}
