package cc.swelink.renderer;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.TrajectoryModule;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Optional;

public final class TrajectoryRenderer {
    private static final RenderType LINES = RenderType.create("paradox_trajectory",
            RenderSetup.builder(RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
                    .withLocation(Paradox.id("pipeline/trajectory"))
                    .withDepthStencilState(Optional.empty()).withCull(false)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build()).createRenderSetup());

    private TrajectoryRenderer() {
    }

    public static void submit(TrajectoryModule module, PoseStack pose, LevelRenderState state, SubmitNodeCollector collector) {
        if (state.cameraRenderState.isPanoramicMode) return;
        var client = Minecraft.getInstance();
        var predictions = module.update(client, state.worldPartialTicks);
        if (predictions.isEmpty()) return;
        Vec3 camera = state.cameraRenderState.pos;
        var projection = new Matrix4f(state.cameraRenderState.projectionMatrix)
                .mul(state.cameraRenderState.viewRotationMatrix);
        var inverse = new Matrix4f(projection).invert();
        int width = client.getWindow().getWidth();
        int height = client.getWindow().getHeight();
        int primary = ThemeColors.primary();
        for (var prediction : predictions) {
            var path = prediction.path();
            if (path.points().size() < 2) continue;
            Vec3 origin = prediction.origin(state.worldPartialTicks).subtract(camera);
            collector.submitCustomGeometry(pose, LINES, (transform, vertices) -> {
                var matrix = transform.pose();
                for (int i = 0; i + 1 < path.points().size(); i++) {
                    Vec3 start = i == 0 ? origin : path.points().get(i).subtract(camera);
                    Vec3 end = path.points().get(i + 1).subtract(camera);
                    var corners = ScreenSpaceLine.quad(projection, inverse, start, end, width, height, 2);
                    if (corners == null) continue;
                    for (Vec3 corner : corners) vertex(vertices, matrix, corner, primary);
                }
            });
        }
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3 point, int color) {
        vertices.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setColor(color);
    }
}
