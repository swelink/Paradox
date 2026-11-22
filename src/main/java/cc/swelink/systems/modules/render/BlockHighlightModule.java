package cc.swelink.systems.modules.render;

import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.HandShaderRenderer;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.render.OutlineShaderSettings;
import cc.swelink.utils.render.PostEffectUtils;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;

public final class BlockHighlightModule extends Module {
    private final OutlineShaderSettings shader = new OutlineShaderSettings(this::addSetting, () -> true);
    private final IntSetting speed = addSetting(new IntSetting("Speed", 12, 1, 30));
    private final HandShaderRenderer renderer = new HandShaderRenderer();
    private final FrameClock clock = new FrameClock();
    private final double[] box = new double[6];
    private float presence;
    private boolean placed;

    public BlockHighlightModule() {
        super("BlockHighlight", "Shaders on the block you look at", Category.RENDER);
    }

    public boolean active() {
        var client = Minecraft.getInstance();
        return isEnabled() && client.player != null && client.level != null && !client.gui.hud.isHidden();
    }

    public void render(CameraRenderState camera, FeatureRenderDispatcher dispatcher, RenderTarget output,
                       GraphicsResourceAllocator allocator) {
        if (!active() || camera.isPanoramicMode) {
            placed = false;
            presence = 0;
            clock.reset();
            return;
        }
        float delta = clock.nextDelta();
        AABB target = target(Minecraft.getInstance());
        if (target != null) {
            double[] goal = {target.minX, target.minY, target.minZ, target.maxX, target.maxY, target.maxZ};
            float blend = 1 - (float) Math.exp(-speed.get() * delta);
            for (int i = 0; i < box.length; i++) {
                box[i] = placed ? box[i] + (goal[i] - box[i]) * blend : goal[i];
            }
            placed = true;
        }
        presence = FrameClock.approach(presence, target != null ? 1 : 0, speed.get(), delta);
        if (!placed || presence <= 0.01f) {
            if (target == null) placed = false;
            return;
        }
        var storage = new SubmitNodeStorage();
        int color = 0xFFFFFFFF;
        var origin = camera.pos;
        float size = FrameClock.smoothStep(presence);
        double centerX = (box[0] + box[3]) / 2, centerY = (box[1] + box[4]) / 2, centerZ = (box[2] + box[5]) / 2;
        double halfX = (box[3] - box[0]) / 2 * size, halfY = (box[4] - box[1]) / 2 * size, halfZ = (box[5] - box[2]) / 2 * size;
        float x0 = (float) (centerX - halfX - origin.x), y0 = (float) (centerY - halfY - origin.y), z0 = (float) (centerZ - halfZ - origin.z);
        float x1 = (float) (centerX + halfX - origin.x), y1 = (float) (centerY + halfY - origin.y), z1 = (float) (centerZ + halfZ - origin.z);
        storage.submitCustomGeometry(new PoseStack(), RenderTypes.lightning(), (transform, vertices) -> {
            var pose = transform.pose();
            quad(vertices, pose, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            quad(vertices, pose, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            quad(vertices, pose, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
            quad(vertices, pose, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            quad(vertices, pose, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            quad(vertices, pose, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        });
        renderer.renderWorld(camera, dispatcher, storage, output, allocator, this::configure);
    }

    private static AABB target(Minecraft client) {
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        var pos = hit.getBlockPos();
        var state = client.level.getBlockState(pos);
        if (state.isAir() || !client.level.getWorldBorder().isWithinBounds(pos)) return null;
        var entity = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
        var shape = state.getShape(client.level, pos, CollisionContext.of(entity));
        if (shape.isEmpty()) return null;
        return shape.bounds().move(pos).inflate(0.002);
    }

    private static void quad(VertexConsumer vertices, Matrix4f pose, int color, float ax, float ay, float az,
                             float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        vertices.addVertex(pose, ax, ay, az).setColor(color);
        vertices.addVertex(pose, bx, by, bz).setColor(color);
        vertices.addVertex(pose, cx, cy, cz).setColor(color);
        vertices.addVertex(pose, dx, dy, dz).setColor(color);
        vertices.addVertex(pose, dx, dy, dz).setColor(color);
        vertices.addVertex(pose, cx, cy, cz).setColor(color);
        vertices.addVertex(pose, bx, by, bz).setColor(color);
        vertices.addVertex(pose, ax, ay, az).setColor(color);
    }

    private void configure(PostChain chain) {
        shader.configure(chain);
        int rgb = ThemeColors.primary();
        PostEffectUtils.writeUniform(chain, "HandColor", ((rgb >>> 16) & 255) / 255f,
                ((rgb >>> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
    }

    public void close() {
        renderer.close();
    }

    @Override
    protected void onDisable() {
        placed = false;
        presence = 0;
        renderer.requestRelease();
    }
}
