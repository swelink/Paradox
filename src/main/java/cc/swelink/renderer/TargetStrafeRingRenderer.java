package cc.swelink.renderer;

import cc.swelink.systems.modules.movement.TargetStrafeModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;

public final class TargetStrafeRingRenderer {
    private static final int SEGMENTS = 96;

    private TargetStrafeRingRenderer() {
    }

    public static void submit(TargetStrafeModule module, PoseStack pose, LevelRenderState state,
                              SubmitNodeCollector collector) {
        if (state.cameraRenderState.isPanoramicMode) return;
        var target = module.ringTarget(Minecraft.getInstance());
        if (target == null) return;
        var center = target.getPosition(state.worldPartialTicks).subtract(state.cameraRenderState.pos).add(0, 0.035, 0);
        float radius = module.orbitRadius();
        float halfWidth = Math.clamp(radius * 0.008f, 0.012f, 0.035f);
        int primary = ThemeColors.primary();
        collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (transform, vertices) -> {
            for (int i = 0; i < SEGMENTS; i++) {
                for (int corner = 0; corner < 4; corner++) {
                    int index = corner < 2 ? i : i + 1;
                    double angle = index * Math.PI * 2 / SEGMENTS;
                    float edge = radius + (corner == 0 || corner == 3 ? -halfWidth : halfWidth);
                    vertices.addVertex(transform.pose(), (float) (center.x + Math.cos(angle) * edge),
                            (float) center.y, (float) (center.z + Math.sin(angle) * edge)).setColor(primary);
                }
            }
        });
    }
}
