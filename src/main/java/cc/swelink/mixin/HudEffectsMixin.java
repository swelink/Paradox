package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.EffectsModule;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudEffectsMixin {
    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void paradox$hideEffects(CallbackInfo ci) {
        if (Paradox.MODULES.get(EffectsModule.class).isEnabled()) ci.cancel();
    }
}
