package cc.swelink.utils.render;

import cc.swelink.systems.modules.render.PostProcessingModule;
import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.AmbienceModule;
import cc.swelink.systems.modules.render.FogModule;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import com.mojang.renderpearl.api.commands.RenderPass;
import cc.swelink.mixin.PreparedFrameInvoker;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;

import java.util.Optional;
import java.util.OptionalDouble;

import java.util.Set;

public final class FogPass {
    private static final Identifier CHAIN = Paradox.id("accent_fog");
    private static final Identifier AMBIENCE = Paradox.id("ambience");
    private static final Identifier AMBIENCE_TRANSLUCENT = Paradox.id("ambience_translucent");
    private static CameraRenderState camera;
    private static final Matrix4f projection = new Matrix4f();
    private static CrossFrameResourcePool pool;

    private FogPass() {
    }

    public static void prepare(CameraRenderState frameCamera, Matrix4f frameProjection, CrossFrameResourcePool resourcePool) {
        camera = frameCamera;
        projection.set(frameProjection);
        pool = resourcePool;
    }

    public static void finish() {
        camera = null;
        pool = null;
        deferredFrame = null;
        deferredPhase = null;
        deferredContext = null;
    }

    private static FeatureRenderDispatcher.PreparedFrame deferredFrame;
    private static FeatureRenderPhase<?> deferredPhase;
    private static FeatureFrameContext deferredContext;

    public static boolean deferCustomGeometry(FeatureRenderDispatcher.PreparedFrame frame, FeatureRenderPhase<?> phase,
                                              FeatureFrameContext context) {
        if (camera == null || pool == null || !PostProcessingModule.active()
                || !wantsAmbience() && !Paradox.MODULES.get(FogModule.class).rendering()) return false;
        deferredFrame = frame;
        deferredPhase = phase;
        deferredContext = context;
        return true;
    }

    public static void drawDeferred(RenderTarget target) {
        if (deferredFrame == null) return;
        var frame = deferredFrame;
        var phase = deferredPhase;
        var context = deferredContext;
        deferredFrame = null;
        deferredPhase = null;
        deferredContext = null;
        var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Paradox custom geometry after fog", target.getColorTextureView(), Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty());
        try {
            RenderSystem.bindDefaultUniforms(pass);
            ((PreparedFrameInvoker) (Object) frame).paradox$executePhase(phase, context, pass);
        } finally {
            pass.close();
        }
    }

    public static void apply(RenderTarget target) {
        if (camera == null || pool == null || !PostProcessingModule.active()) return;
        var fog = Minecraft.getInstance().getShaderManager().getPostChain(CHAIN, Set.of(PostChain.MAIN_TARGET_ID));
        if (fog != null && Paradox.MODULES.get(FogModule.class).configure(fog, camera, projection)) process(fog, target);
    }

    public static boolean wantsAmbience() {
        return camera != null && pool != null && PostProcessingModule.active()
                && Paradox.MODULES.get(AmbienceModule.class).rendering();
    }

    public static PostChain ambience() {
        if (camera == null || pool == null) return null;
        var chain = Minecraft.getInstance().getShaderManager().getPostChain(AMBIENCE, Set.of(PostChain.MAIN_TARGET_ID));
        return chain != null && Paradox.MODULES.get(AmbienceModule.class).configure(chain, camera, projection, false) ? chain : null;
    }

    public static void applyAmbience(PostChain chain, RenderTarget target) {
        process(chain, target);
    }

    private static SwitchingRenderPass split;
    private static RenderTarget splitTarget;
    private static boolean splitPending;
    private static boolean translucentPending;

    public static RenderPass beginSplit(RenderPass pass, RenderTarget target) {
        split = new SwitchingRenderPass(pass);
        splitTarget = target;
        splitPending = true;
        return split.proxy();
    }

    public static void splitIfPending() {
        if (!splitPending || split == null) return;
        splitPending = false;
        split.target().close();
        var chain = ambience();
        if (chain != null) {
            applyAmbience(chain, splitTarget);
            translucentPending = true;
        }
        var reopened = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Paradox translucent after ambience", splitTarget.getColorTextureView(), Optional.empty(),
                splitTarget.getDepthTextureView(), OptionalDouble.empty());
        RenderSystem.bindDefaultUniforms(reopened);
        split.target(reopened);
    }

    public static void endSplit(RenderPass original) {
        if (split == null) return;
        if (splitPending) {
            splitPending = false;
            split.target().close();
            var chain = ambience();
            if (chain != null) {
                applyAmbience(chain, splitTarget);
                translucentPending = true;
            }
        } else if (split.target() != original) {
            split.target().close();
        }
        if (translucentPending) {
            translucentPending = false;
            var translucent = Minecraft.getInstance().getShaderManager()
                    .getPostChain(AMBIENCE_TRANSLUCENT, Set.of(PostChain.MAIN_TARGET_ID));
            if (translucent != null && Paradox.MODULES.get(AmbienceModule.class).configure(translucent, camera, projection, true)) {
                process(translucent, splitTarget);
            }
        }
        split = null;
        splitTarget = null;
    }


    private static void process(PostChain chain, RenderTarget target) {
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        var previousType = RenderSystem.getProjectionType();
        try {
            chain.process(target, pool);
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, previousType);
        }
    }
}
