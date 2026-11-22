package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.movement.NoJumpDelayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityNoJumpDelayMixin {
    @Shadow private int noJumpDelay;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void paradox$removeJumpDelay(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player
                && Paradox.MODULES.get(NoJumpDelayModule.class).isEnabled()) {
            noJumpDelay = 0;
        }
    }
}
