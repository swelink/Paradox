package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.BlockHighlightModule;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererBlockHighlightMixin {
    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void paradox$hideBlockOutline(CallbackInfo ci) {
        if (Paradox.MODULES.get(BlockHighlightModule.class).active()) ci.cancel();
    }
}
