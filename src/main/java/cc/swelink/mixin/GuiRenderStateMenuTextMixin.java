package cc.swelink.mixin;

import cc.swelink.gui.screens.MenuText;

import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMenuTextMixin {
    @Inject(method = "addText", at = @At("HEAD"), cancellable = true)
    private void paradox$menuText(GuiTextRenderState state, CallbackInfo ci) {
        var text = (GuiTextRenderStateAccessor) (Object) state;
        if (text.paradox$backgroundColor() != 0) return;
        if (MenuText.draw(text.paradox$font(), text.paradox$text(), state.pose, state.scissor,
                text.paradox$x(), text.paradox$y(), text.paradox$color())) {
            ci.cancel();
        }
    }
}
