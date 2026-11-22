package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.TimeModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
public abstract class ClientClockInstanceMixin {
    @Unique
    private static final long PARADOX_DAY_TICKS = 24000;

    @Shadow private long totalTicks;
    @Shadow private float partialTick;

    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
    private void paradox$timeTicks(CallbackInfoReturnable<Long> cir) {
        var module = Paradox.MODULES.get(TimeModule.class);
        if (!module.active()) return;
        float dayTime = module.dayTime(totalTicks, partialTick);
        cir.setReturnValue(Math.floorDiv(totalTicks, PARADOX_DAY_TICKS) * PARADOX_DAY_TICKS + (long) dayTime);
    }

    @Inject(method = "partialTick", at = @At("HEAD"), cancellable = true)
    private void paradox$timePartial(CallbackInfoReturnable<Float> cir) {
        var module = Paradox.MODULES.get(TimeModule.class);
        if (!module.active()) return;
        float dayTime = module.dayTime(totalTicks, partialTick);
        cir.setReturnValue(dayTime - (float) Math.floor(dayTime));
    }
}
