package cc.swelink.mixin;

import cc.swelink.utils.combat.CriticalHitTiming;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerCriticalSprintMixin {
    @ModifyReturnValue(method = "shouldStopRunSprinting", at = @At("RETURN"))
    private boolean paradox$stopSprint(boolean original) {
        return original || CriticalHitTiming.stoppingSprint();
    }

    @ModifyReturnValue(method = "canStartSprinting", at = @At("RETURN"))
    private boolean paradox$holdSprint(boolean original) {
        return original && !CriticalHitTiming.stoppingSprint();
    }
}
