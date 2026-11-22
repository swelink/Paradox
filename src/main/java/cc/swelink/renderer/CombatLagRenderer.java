package cc.swelink.renderer;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.BacktrackModule;
import cc.swelink.systems.modules.combat.FakeLagModule;
import cc.swelink.systems.modules.combat.KnockbackDelayModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CombatLagRenderer {
    private static final RenderType BOX = RenderType.create("paradox_combat_lag_box",
            RenderSetup.builder(RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
                    .withLocation(Paradox.id("pipeline/combat_lag_box"))
                    .withDepthStencilState(Optional.empty()).withCull(false)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build()).createRenderSetup());
    private static final int[][] FACES = {
            {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1},
            {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}
    };

    private static final float SMOOTHING = 18;
    private static final Map<String, AABB> displayed = new HashMap<>();
    private static long lastFrame;

    private CombatLagRenderer() {
    }

    public static void submit(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector) {
        if (state.cameraRenderState.isPanoramicMode) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        var targets = new HashMap<String, AABB>();
        var backtrack = Paradox.MODULES.get(BacktrackModule.class).serverBox(client);
        if (backtrack != null) targets.put("backtrack", backtrack);
        var fakeLag = Paradox.MODULES.get(FakeLagModule.class).serverBox(client);
        if (fakeLag != null) targets.put("fakelag", fakeLag);
        Paradox.MODULES.get(KnockbackDelayModule.class).serverBoxes(client)
                .forEach((id, box) -> targets.put("knockback:" + id, box));
        long now = System.nanoTime();
        float delta = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1.0e9f, 0.1f);
        lastFrame = now;
        float amount = 1 - (float) Math.exp(-SMOOTHING * delta);
        displayed.keySet().retainAll(targets.keySet());
        for (var target : targets.entrySet()) {
            var current = displayed.get(target.getKey());
            var box = current == null || target.getKey().equals("fakelag") ? target.getValue()
                    : lerp(current, target.getValue(), amount);
            displayed.put(target.getKey(), box);
            drawBox(box, ARGB.multiplyAlpha(ThemeColors.primary(), 0.18f), pose, state, collector);
        }
    }

    private static AABB lerp(AABB from, AABB to, float amount) {
        return new AABB(from.minX + (to.minX - from.minX) * amount, from.minY + (to.minY - from.minY) * amount,
                from.minZ + (to.minZ - from.minZ) * amount, from.maxX + (to.maxX - from.maxX) * amount,
                from.maxY + (to.maxY - from.maxY) * amount, from.maxZ + (to.maxZ - from.maxZ) * amount);
    }

    public static void drawBox(AABB box, int fill, PoseStack pose, LevelRenderState state, SubmitNodeCollector collector) {
        if (box == null) return;
        var camera = state.cameraRenderState.pos;
        var corners = new Vec3[8];
        for (int index = 0; index < corners.length; index++) {
            corners[index] = new Vec3((index & 1) == 0 ? box.minX : box.maxX,
                    (index & 2) == 0 ? box.minY : box.maxY,
                    (index & 4) == 0 ? box.minZ : box.maxZ).subtract(camera);
        }
        collector.submitCustomGeometry(pose, BOX, (transform, vertices) -> {
            var matrix = transform.pose();
            for (var face : FACES) {
                for (int index : face) vertex(vertices, matrix, corners[index], fill);
            }
        });
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3 point, int color) {
        vertices.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setColor(color);
    }
}
