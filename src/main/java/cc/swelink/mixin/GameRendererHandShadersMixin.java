package cc.swelink.mixin;

import cc.swelink.systems.modules.render.PostProcessingModule;
import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.HandShadersModule;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererHandShadersMixin {
    @Shadow @Final private RenderTarget mainRenderTarget;
    @Shadow @Final private CrossFrameResourcePool resourcePool;
    @Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;

    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target =
            "Lcom/mojang/renderpearl/api/commands/RenderPass;close()V", ordinal = 0, shift = At.Shift.AFTER))
    private void paradox$handShader(CameraRenderState camera, PlayerRenderState player, GpuTextureView depth,
                                  CallbackInfo ci, @Local FeatureRenderDispatcher.PreparedFrame frame) {
        var module = Paradox.MODULES.get(HandShadersModule.class);
        if (module.handsActive() && PostProcessingModule.active()) {
            module.renderer().render(frame, mainRenderTarget, resourcePool, module);
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void paradox$beginHandShaderFrame(CallbackInfo ci) {
        Paradox.MODULES.get(HandShadersModule.class).renderer().beginFrame();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
            shift = At.Shift.AFTER))
    private void paradox$thirdPersonShader(CallbackInfo ci, @Local CameraRenderState camera) {
        if (!PostProcessingModule.active()) return;
        var module = Paradox.MODULES.get(HandShadersModule.class);
        module.renderer().renderHeldItems(camera, featureRenderDispatcher, mainRenderTarget, resourcePool, module);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void paradox$closeHandShader(CallbackInfo ci) {
        for (var module : Paradox.MODULES.modules()) {
            if (module instanceof HandShadersModule hands) {
                hands.renderer().close();
            }
        }
    }
}
