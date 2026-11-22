package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.gui.screens.HudChatOverlay;
import cc.swelink.systems.modules.client.ClickGuiModule;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenModuleListMixin extends Screen {
    @Unique
    private HudChatOverlay paradox$hudOverlay;

    protected ChatScreenModuleListMixin(Component title) {
        super(title);
    }

    @Unique
    private HudChatOverlay paradox$overlay() {
        if (paradox$hudOverlay == null) {
            paradox$hudOverlay = new HudChatOverlay(Paradox.MODULES, Paradox.MODULES.get(ClickGuiModule.class)::accentColor);
        }
        return paradox$hudOverlay;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void paradox$initHudOverlay(CallbackInfo ci) {
        paradox$overlay().init(width, height);
    }

    @Inject(method = "resize", at = @At("TAIL"))
    private void paradox$resizeHudOverlay(int width, int height, CallbackInfo ci) {
        paradox$overlay().resize(width, height);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void paradox$renderHudOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        paradox$overlay().extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void paradox$interactWithHud(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> ci) {
        if (paradox$overlay().mouseClicked(event, doubleClick)) ci.setReturnValue(true);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return paradox$overlay().mouseDragged(event, deltaX, deltaY) || super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return paradox$overlay().mouseReleased(event) || super.mouseReleased(event);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void paradox$stopHudEditing(CallbackInfo ci) {
        if (paradox$hudOverlay != null) paradox$hudOverlay.removed();
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void paradox$resizeHud(double mouseX, double mouseY, double horizontal, double vertical,
                                CallbackInfoReturnable<Boolean> ci) {
        if (paradox$overlay().mouseScrolled(mouseX, mouseY, horizontal, vertical)) ci.setReturnValue(true);
    }

}
