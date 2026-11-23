package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.events.OverlayRenderEvent;
import cc.swelink.gui.screens.HudEditorScreen;
import cc.swelink.renderer.BlurRenderer;
import cc.swelink.systems.hud.HudElement;
import cc.swelink.systems.hud.HudOrder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiModuleListMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void paradox$hudEditorGrid(CallbackInfo ci) {
        if (!(minecraft.gui.screen() instanceof HudEditorScreen)) return;
        var window = minecraft.getWindow();
        var graphics = new GuiGraphicsExtractor(minecraft, guiRenderState,
                (int) minecraft.mouseHandler.getScaledXPos(window), (int) minecraft.mouseHandler.getScaledYPos(window));
        HudEditorScreen.drawGrid(minecraft, graphics);
        graphics.nextStratum();
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER))
    private void paradox$moduleList(CallbackInfo ci) {
        if (minecraft.player == null || minecraft.level == null) return;
        var window = minecraft.getWindow();
        var graphics = new GuiGraphicsExtractor(minecraft, guiRenderState,
                (int) minecraft.mouseHandler.getScaledXPos(window), (int) minecraft.mouseHandler.getScaledYPos(window));
        graphics.nextStratum();
        BlurRenderer.endGroup(graphics);
        Paradox.EVENTS.post(new OverlayRenderEvent(minecraft, graphics));
        for (HudElement hud : HudOrder.ordered(Paradox.MODULES.modules())) {
            hud.render(minecraft, graphics);
        }
        BlurRenderer.endGroup(graphics);
        graphics.nextStratum();
    }
}
