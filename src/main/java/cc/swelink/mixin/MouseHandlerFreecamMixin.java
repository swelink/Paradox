package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.FreecamModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerFreecamMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void paradox$freecamScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.gui.screen() == null && window == client.getWindow().handle()
                && Paradox.MODULES.get(FreecamModule.class).scroll(vertical)) {
            ci.cancel();
        }
    }
}
