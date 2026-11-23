package cc.swelink.renderer;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.TargetEspModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Optional;

public final class TargetEspRenderer {
    private static final int HURT_COLOR = 0xFFFF5252;
    private static final Identifier CROSSHAIR = Paradox.id("icons/hud/crosshair.png");
    private static final RenderType CROSSHAIR_RENDER_TYPE = RenderType.create("paradox_target_crosshair",
            RenderSetup.builder(RenderPipeline.builder()
                    .withLocation(Paradox.id("pipeline/target_crosshair"))
                    .withVertexShader(Identifier.withDefaultNamespace("core/position_tex_color"))
                    .withFragmentShader(Paradox.id("core/target_crosshair"))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withDepthStencilState(Optional.empty()).withCull(false)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build())
                    .withTexture("Sampler0", CROSSHAIR,
                            () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)).createRenderSetup());
    private static final RenderType GHOST = RenderType.create("paradox_target_ghost",
            RenderSetup.builder(RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
                    .withLocation(Paradox.id("pipeline/target_ghost"))
                    .withDepthStencilState(Optional.empty()).withCull(false)
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)).build()).createRenderSetup());
    private static final int RINGS = 4;
    private static final int SEGMENTS = 16;
    private static final RenderType RING = RenderType.create("paradox_target_ring",
            RenderSetup.builder(RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
                    .withLocation(Paradox.id("pipeline/target_ring"))
                    .withDepthStencilState(Optional.empty()).withCull(false)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build()).createRenderSetup());
    private static final int RING_POINTS = 96;
    private static final int RING_TRAIL_STEPS = 10;
    private static final double RING_TRAIL_GAP = 0.05;
    private static final float RING_WIDTH = 0.015f;
    private static final float RING_MARGIN = 0.05f;
    private static final double RING_CYCLE = 2.2;

    private TargetEspRenderer() {
    }

    public static void submit(TargetEspModule module, PoseStack pose, LevelRenderState state,
                              SubmitNodeCollector collector) {
        if (state.cameraRenderState.isPanoramicMode) return;
        var client = Minecraft.getInstance();
        var snapshot = module.snapshot(client);
        if (snapshot == null || client.level == null) return;
        var target = snapshot.target();
        var center = target.getPosition(state.worldPartialTicks)
                .add(0, target.getBbHeight() * 0.55, 0).subtract(state.cameraRenderState.pos);
        double time = snapshot.time();
        int color = hurtColor(target, ThemeColors.primary(), state.worldPartialTicks);
        if (snapshot.mode().equals("Crosshair")) {
            crosshair(snapshot, pose, state, collector, center, time, color);
            return;
        }
        if (snapshot.mode().equals("Ring")) {
            ring(snapshot, pose, state, collector, time, color);
            return;
        }
        float radius = Math.max(0.45f, target.getBbWidth() * 1.05f);
        var rightVector = new Vector3f(1, 0, 0).rotate(state.cameraRenderState.orientation);
        var upVector = new Vector3f(0, 1, 0).rotate(state.cameraRenderState.orientation);
        var right = new Vec3(rightVector.x, rightVector.y, rightVector.z);
        var up = new Vec3(upVector.x, upVector.y, upVector.z);
        collector.submitCustomGeometry(pose, GHOST, (transform, vertices) -> {
            for (int trail = 0; trail < 3; trail++) {
                double plane = trail * Math.PI * 2 / 3 + time * 0.35;
                var axis = new Vec3(Math.cos(plane), 0, Math.sin(plane));
                var tilt = new Vec3(-Math.sin(plane) * 0.55, 0.835, Math.cos(plane) * 0.55);
                int samples = Math.round(snapshot.length() * 1.25f);
                for (int index = samples - 1; index >= 0; index--) {
                    float fraction = index / (float) samples;
                    double angle = time * 4.2 + plane - index * 0.06;
                    var position = center.add(axis.scale(Math.cos(angle) * radius))
                            .add(tilt.scale(Math.sin(angle) * radius));
                    float fade = (float) Math.pow(1 - fraction, 1.6);
                    float alpha = snapshot.opacity() * fade * 0.55f;
                    float particleSize = snapshot.size() * 0.35f * (1 - fraction * 0.75f);
                    glow(vertices, transform.pose(), position, right, up, particleSize, color, alpha);
                    if (index == 0) {
                        glow(vertices, transform.pose(), position, right, up, particleSize * 0.45f,
                                ARGB.srgbLerp(0.55f, color, 0xFFFFFFFF), snapshot.opacity() * 0.8f);
                    }
                }
            }
        });
    }

    private static void crosshair(TargetEspModule.Snapshot snapshot, PoseStack pose, LevelRenderState state,
                                  SubmitNodeCollector collector, Vec3 center, double time, int color) {
        pose.pushPose();
        try {
            pose.translate(center);
            pose.rotate(state.cameraRenderState.orientation);
            pose.rotateDegrees(Axis.ZP, (float) (180 * (1 - Math.cos(time * 1.6))));
            float halfSize = snapshot.size() * (0.9f + snapshot.opacity() * 0.1f) * 0.5f;
            int tint = ARGB.multiplyAlpha(color, snapshot.opacity());
            collector.submitCustomGeometry(pose, CROSSHAIR_RENDER_TYPE, (transform, vertices) -> {
                texturedVertex(vertices, transform.pose(), -halfSize, halfSize, 0, 0, tint);
                texturedVertex(vertices, transform.pose(), -halfSize, -halfSize, 0, 1, tint);
                texturedVertex(vertices, transform.pose(), halfSize, -halfSize, 1, 1, tint);
                texturedVertex(vertices, transform.pose(), halfSize, halfSize, 1, 0, tint);
            });
        } finally {
            pose.popPose();
        }
    }

    private static void ring(TargetEspModule.Snapshot snapshot, PoseStack pose, LevelRenderState state,
                             SubmitNodeCollector collector, double time, int color) {
        var target = snapshot.target();
        var base = target.getPosition(state.worldPartialTicks).subtract(state.cameraRenderState.pos);
        float height = target.getBbHeight();
        float scale = snapshot.size() / 0.75f;
        float radius = (target.getBbWidth() * 0.5f + 0.35f) * scale;
        float opacity = snapshot.opacity();
        double phase = time * RING_CYCLE;
        float motion = (float) Math.sqrt(Math.abs(Math.sin(phase)));
        double y = ringHeight(base.y, height, phase);
        collector.submitCustomGeometry(pose, RING, (transform, vertices) -> {
            var matrix = transform.pose();
            for (int step = 0; step < RING_TRAIL_STEPS; step++) {
                float near = step / (float) RING_TRAIL_STEPS;
                float far = (step + 1) / (float) RING_TRAIL_STEPS;
                double nearY = ringHeight(base.y, height, phase - step * RING_TRAIL_GAP);
                double farY = ringHeight(base.y, height, phase - (step + 1) * RING_TRAIL_GAP);
                int nearColor = ARGB.multiplyAlpha(color, opacity * motion * 0.3f * (float) Math.pow(1 - near, 1.5));
                int farColor = ARGB.multiplyAlpha(color, opacity * motion * 0.3f * (float) Math.pow(1 - far, 1.5));
                for (int point = 0; point < RING_POINTS; point++) {
                    double first = point * Math.PI * 2 / RING_POINTS;
                    double second = (point + 1) * Math.PI * 2 / RING_POINTS;
                    ringVertex(vertices, matrix, base, radius, first, nearY, nearColor);
                    ringVertex(vertices, matrix, base, radius, first, farY, farColor);
                    ringVertex(vertices, matrix, base, radius, second, farY, farColor);
                    ringVertex(vertices, matrix, base, radius, second, nearY, nearColor);
                }
            }
            ribbon(vertices, matrix, base, y, radius, RING_WIDTH * scale, ARGB.multiplyAlpha(color, opacity));
        });
    }

    private static void ribbon(VertexConsumer vertices, Matrix4f matrix, Vec3 base, double y, float radius,
                               float width, int color) {
        for (int point = 0; point < RING_POINTS; point++) {
            double first = point * Math.PI * 2 / RING_POINTS;
            double second = (point + 1) * Math.PI * 2 / RING_POINTS;
            var firstPosition = new Vec3(base.x + Math.cos(first) * radius, y, base.z + Math.sin(first) * radius);
            var secondPosition = new Vec3(base.x + Math.cos(second) * radius, y, base.z + Math.sin(second) * radius);
            var firstSide = ribbonSide(firstPosition, first);
            var secondSide = ribbonSide(secondPosition, second);
            ribbonVertex(vertices, matrix, firstPosition.add(firstSide.scale(-width)), color);
            ribbonVertex(vertices, matrix, firstPosition.add(firstSide.scale(width)), color);
            ribbonVertex(vertices, matrix, secondPosition.add(secondSide.scale(width)), color);
            ribbonVertex(vertices, matrix, secondPosition.add(secondSide.scale(-width)), color);
        }
    }

    private static Vec3 ribbonSide(Vec3 position, double angle) {
        var tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
        var side = tangent.cross(position.scale(-1));
        double length = side.length();
        return length < 1e-6 ? new Vec3(0, 1, 0) : side.scale(1 / length);
    }

    private static void ribbonVertex(VertexConsumer vertices, Matrix4f matrix, Vec3 position, int color) {
        vertices.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z).setColor(color);
    }

    private static void ringVertex(VertexConsumer vertices, Matrix4f matrix, Vec3 base, float radius, double angle,
                                   double y, int color) {
        vertices.addVertex(matrix, (float) (base.x + Math.cos(angle) * radius), (float) y,
                (float) (base.z + Math.sin(angle) * radius)).setColor(color);
    }

    private static double ringHeight(double baseY, float height, double phase) {
        return baseY + RING_MARGIN + (height - RING_MARGIN * 2) * (0.5 - 0.5 * Math.cos(phase));
    }

    private static int hurtColor(Entity target, int color, float partialTicks) {
        if (!(target instanceof LivingEntity living) || living.hurtTime <= 0 || living.hurtDuration <= 0) return color;
        float hurt = Math.clamp((living.hurtTime - partialTicks) / living.hurtDuration, 0, 1);
        return ARGB.srgbLerp(hurt, color, HURT_COLOR);
    }

    private static void texturedVertex(VertexConsumer vertices, Matrix4f matrix, float x, float y,
                                       float u, float v, int color) {
        vertices.addVertex(matrix, x, y, 0).setColor(color).setUv(u, v);
    }

    private static void glow(VertexConsumer vertices, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                             float radius, int color, float alpha) {
        for (int ring = 0; ring < RINGS; ring++) {
            float inner = ring / (float) RINGS;
            float outer = (ring + 1) / (float) RINGS;
            int innerColor = glowColor(color, inner, alpha);
            int outerColor = glowColor(color, outer, alpha);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double first = segment * Math.PI * 2 / SEGMENTS;
                double second = (segment + 1) * Math.PI * 2 / SEGMENTS;
                vertex(vertices, matrix, center, right, up, inner * radius, first, innerColor);
                vertex(vertices, matrix, center, right, up, outer * radius, first, outerColor);
                vertex(vertices, matrix, center, right, up, outer * radius, second, outerColor);
                vertex(vertices, matrix, center, right, up, inner * radius, second, innerColor);
            }
        }
    }

    private static int glowColor(int color, float distance, float alpha) {
        float falloff = (float) ((Math.exp(-5 * distance * distance) - Math.exp(-5)) / (1 - Math.exp(-5)));
        int tint = ARGB.srgbLerp((1 - distance) * 0.3f, color, 0xFFFFFFFF);
        return ARGB.multiplyAlpha(tint, alpha * falloff);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                                float radius, double angle, int color) {
        double horizontal = Math.cos(angle) * radius;
        double vertical = Math.sin(angle) * radius;
        vertices.addVertex(matrix, (float) (center.x + right.x * horizontal + up.x * vertical),
                (float) (center.y + right.y * horizontal + up.y * vertical),
                (float) (center.z + right.z * horizontal + up.z * vertical)).setColor(color);
    }
}
