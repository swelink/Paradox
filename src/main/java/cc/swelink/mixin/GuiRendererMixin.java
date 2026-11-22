package cc.swelink.mixin;

import cc.swelink.renderer.BlurRenderer;
import cc.swelink.renderer.EspPreviewRenderState;
import cc.swelink.renderer.EspPreviewRenderer;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.UniformGuiFade;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow
    @Final
    private GuiRenderState renderState;

    @Shadow
    @Final
    private List<?> draws;

    @Unique
    private final TreeMap<Integer, Consumer<RenderTarget>> paradox$captures = new TreeMap<>();

    @Shadow
    @Final
    @Mutable
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void paradox$registerEspPreview(CallbackInfo ci) {
        pictureInPictureRenderers = new HashMap<>(pictureInPictureRenderers);
        pictureInPictureRenderers.put(EspPreviewRenderState.class, new EspPreviewRenderer());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void paradox$prepareBlur(CallbackInfo ci) {
        paradox$captures.clear();
        UniformGuiFade.prepare(renderState);
        BlurRenderer.prepareFrame(renderState);
    }

    @Inject(method = "addElementToMesh", at = @At("TAIL"))
    private void paradox$recordFadeStart(GuiElementRenderState element, CallbackInfo ci) {
        if (UniformGuiFade.isStart(element)) paradox$captures.put(draws.size() - 1, UniformGuiFade::capture);
        var blur = BlurRenderer.captureFor(element);
        if (blur != null) paradox$captures.put(draws.size() - 1, blur);
    }

    @WrapMethod(method = "executeDrawRange")
    private void paradox$drawUniformFade(Supplier<String> label, RenderTarget target, GpuBufferSlice transform,
                                      int first, int last, Operation<Void> original) {
        int start = first;
        for (var capture : paradox$captures.subMap(first, true, last, false).entrySet()) {
            int index = capture.getKey();
            if (start < index) original.call(label, target, transform, start, index);
            capture.getValue().accept(target);
            start = index + 1;
        }
        if (start < last) original.call(label, target, transform, start, last);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void paradox$finishUniformFade(CallbackInfo ci) {
        UniformGuiFade.finish(renderState);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void paradox$releaseBlur(CallbackInfo ci) {
        BlurRenderer.shutdown();
        UniformGuiFade.shutdown();
        TextRenderer.shutdown();
    }
}
