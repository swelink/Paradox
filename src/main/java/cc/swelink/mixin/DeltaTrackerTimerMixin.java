package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.world.TimerModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
    @ModifyExpressionValue(method = "advanceGameTime", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/floats/FloatUnaryOperator;apply(F)F"))
    private float paradox$tickDuration(float original) {
        var client = Minecraft.getInstance();
        if (client == null || client.getDeltaTracker() != (Object) this || client.player == null
                || client.level == null || client.isPaused()) return original;
        return original / Paradox.MODULES.get(TimerModule.class).multiplier();
    }
}
