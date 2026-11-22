package cc.swelink.gui.previews;

import cc.swelink.renderer.EspPreviewRenderState;
import cc.swelink.renderer.EspProjection;
import cc.swelink.renderer.GuiGraphicsAccess;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.systems.modules.render.esp.EspModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class EspPlayerPreview {
    public static final float HEIGHT = 164;
    private float cameraYaw;
    private float cameraPitch;

    public void drag(float deltaX, float deltaY) {
        cameraYaw = (cameraYaw + deltaX * 1.5f) % 360;
        cameraPitch = Math.clamp(cameraPitch + deltaY, -65, 65);
    }

    public void draw(Renderer2D renderer, EspModule module, float x, float y, float width) {
        if (renderer.opacity() == 0) return;
        var client = Minecraft.getInstance();
        float center = x + width / 2;
        renderer.sdf.roundedRect(x + 4, y + 4, width - 8, HEIGHT - 8, 3, 0xFF242428);
        int hintColor = 0xFFFFFFFF;
        renderer.text.centered("Drag to orbit camera", center, y + HEIGHT - 17, 10, hintColor);
        if (client.player == null) {
            renderer.text.centered("Join a world to preview", center, y + HEIGHT / 2, 10, hintColor);
            return;
        }
        var state = client.getEntityRenderDispatcher().extractEntity(client.player, 1);
        state.outlineColor = 0;
        state.nameTag = null;
        state.scoreText = null;
        state.shadowPieces.clear();
        state.displayFireAnimation = false;
        state.isInvisible = false;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180;
            living.yRot = 0;
            living.xRot = 0;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1;
        }
        var graphics = renderer.graphics();
        var pose = graphics.pose();
        var topLeft = new org.joml.Vector2f(x + 8, y + 7).mulPosition(pose);
        var bottomRight = new org.joml.Vector2f(x + width - 8, y + HEIGHT - 24).mulPosition(pose);
        int x0 = Math.round(topLeft.x);
        int y0 = Math.round(topLeft.y);
        int x1 = Math.round(bottomRight.x);
        int y1 = Math.round(bottomRight.y);
        var access = GuiGraphicsAccess.of(graphics);
        var scissor = access.paradox$scissorArea();
        var bounds = PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor);
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        var rotation = new Quaternionf().rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(cameraPitch)).rotateY((float) Math.toRadians(cameraYaw));
        var shader = module.shader();
        float previewScale = (y1 - y0) / 2.5f;
        access.paradox$renderState().addPicturesInPictureState(new EspPreviewRenderState(state, rotation,
                x0, y0, x1, y1, previewScale, renderer.opacity(), module.shaderSelected(), module.color(),
                shader.width(), shader.fill(), shader.glow(), shader.shape(), shader.waveMode(), scissor, bounds));
        if (!module.shaderSelected()) {
            float halfWidth = state.boundingBoxWidth / 2;
            var box = new AABB(-halfWidth, 0, -halfWidth, halfWidth, state.boundingBoxHeight, halfWidth);
            var projection = new Matrix4f().setOrtho(0, x1 - x0, y1 - y0, 0, -1000, 1000)
                    .translate((x1 - x0) / 2f, (y1 - y0) / 2f, 0)
                    .scale(previewScale, previewScale, -previewScale)
                    .rotate(rotation).translate(0, -state.boundingBoxHeight / 2, 0);
            var projected = EspProjection.project(box, Vec3.ZERO, projection, x1 - x0, y1 - y0);
            if (projected != null) {
                float boxX = (x0 + projected.x() - pose.m20()) / pose.m00() - 4;
                float boxY = (y0 + projected.y() - pose.m21()) / pose.m11() - 4;
                module.boxes().drawBox(renderer, boxX, boxY,
                        projected.width() / pose.m00() + 8, projected.height() / pose.m11() + 8,
                        client.player.getHealth() / Math.max(1, client.player.getMaxHealth()));
            }
        }
    }
}
