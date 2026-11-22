package cc.swelink.renderer;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.HandShadersModule;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

public final class HandShaderRenderer implements AutoCloseable {
    public static final Identifier POST_EFFECT = Paradox.id("hand_shaders");
    private TextureTarget mask;
    private SubmitNodeStorage heldItems = new SubmitNodeStorage();
    private boolean releaseRequested;

    public void render(FeatureRenderDispatcher.PreparedFrame frame, RenderTarget output,
                       GraphicsResourceAllocator allocator, HandShadersModule module) {
        if (!module.active()) {
            close();
            return;
        }
        render(frame, output, allocator, module::configure);
    }

    public void render(FeatureRenderDispatcher.PreparedFrame frame, RenderTarget output,
                       GraphicsResourceAllocator allocator, Consumer<PostChain> configure) {
        if (releaseRequested) {
            close();
        }
        if (frame.isEmpty()) {
            return;
        }
        var client = Minecraft.getInstance();
        var chain = client.getShaderManager().getPostChain(POST_EFFECT, Set.of(PostChain.MAIN_TARGET_ID));
        if (chain == null) {
            return;
        }
        ensureTarget(output.width, output.height);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var pass = encoder.createRenderPass(() -> "Paradox hand shader mask", mask.getColorTextureView(),
                Optional.of(new Vector4f()), mask.getDepthTextureView(), OptionalDouble.of(RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE))) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        }
        var projection = RenderSystem.getProjectionMatrixBuffer();
        var projectionType = RenderSystem.getProjectionType();
        try {
            configure.accept(chain);
            chain.process(mask, allocator);
            mask.blitAndBlendToTexture(output.getColorTextureView(), null);
        } finally {
            RenderSystem.setProjectionMatrix(projection, projectionType);
        }
    }

    public void beginFrame() {
        heldItems = new SubmitNodeStorage();
        if (releaseRequested) {
            close();
        }
    }

    public void captureHeldItem(ArmedEntityRenderState state, ItemStackRenderState item,
                                PoseStack pose, int light, int overlay, HandShadersModule module) {
        var player = Minecraft.getInstance().player;
        if (module.thirdPersonActive() && state instanceof AvatarRenderState avatar
                && player != null && avatar.id == player.getId()) {
            item.submit(pose, heldItems, light, overlay, 0);
        }
    }

    public void renderHeldItems(CameraRenderState camera, FeatureRenderDispatcher dispatcher,
                                RenderTarget output, GraphicsResourceAllocator allocator, HandShadersModule module) {
        if (!module.thirdPersonActive() || camera.isPanoramicMode) {
            return;
        }
        renderWorld(camera, dispatcher, heldItems, output, allocator, module::configure);
    }

    public void renderWorld(CameraRenderState camera, FeatureRenderDispatcher dispatcher, SubmitNodeStorage storage,
                            RenderTarget output, GraphicsResourceAllocator allocator, Consumer<PostChain> configure) {
        var view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        try {
            view.mul(camera.viewRotationMatrix);
            try (var frame = dispatcher.prepareFrame(storage)) {
                render(frame, output, allocator, configure);
            }
        } finally {
            view.popMatrix();
        }
    }

    private void ensureTarget(int width, int height) {
        if (mask != null && mask.width == width && mask.height == height) {
            return;
        }
        close();
        mask = new TextureTarget("Paradox hand shader mask", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
    }

    public void requestRelease() {
        releaseRequested = true;
    }

    @Override
    public void close() {
        if (mask != null) {
            mask.destroyBuffers();
            mask = null;
        }
        releaseRequested = false;
    }
}
