package cc.swelink.mixin;

import cc.swelink.gui.screens.MenuText;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsCenteredTextMixin {
    @Inject(method = "centeredText*", at = @At("HEAD"))
    private void paradox$beginCentered(CallbackInfo ci) {
        MenuText.centered(true);
    }

    @Inject(method = "centeredText*", at = @At("RETURN"))
    private void paradox$endCentered(CallbackInfo ci) {
        MenuText.centered(false);
    }
}
