package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.movement.NoSlowModule;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerNoSlowMixin {
    @ModifyExpressionValue(method = "modifyInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean paradox$noSlow(boolean using) {
        return Paradox.MODULES.get(NoSlowModule.class).itemSlowdown((LocalPlayer) (Object) this, using);
    }

    @ModifyExpressionValue(method = {"canStartSprinting", "aiStep"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isSlowDueToUsingItem()Z"))
    private boolean paradox$sprintWhileUsing(boolean slowed) {
        return slowed && !Paradox.MODULES.get(NoSlowModule.class).isEnabled();
    }

    @ModifyReturnValue(method = "itemUseSpeedMultiplier", at = @At("RETURN"))
    private float paradox$speedMultiplier(float original) {
        return Paradox.MODULES.get(NoSlowModule.class).speedMultiplier(original);
    }
}
