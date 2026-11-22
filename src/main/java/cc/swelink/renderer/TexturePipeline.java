package cc.swelink.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.joml.Matrix3x2fc;

public final class TexturePipeline {
    private final Renderer2D renderer;

    TexturePipeline(Renderer2D renderer) {
        this.renderer = renderer;
    }

    public void image(Identifier texture, float x, float y, float width, float height, int tint) {
        region(texture, x, y, width, height, 0, 0, 1, 1, tint);
    }

    public void icon(Identifier texture, float x, float y, float size, int tint) {
        var loadedTexture = IconTexture.get(texture);
        quad(Pipelines.ICON, loadedTexture.getTextureView(), loadedTexture.getSampler(),
                x, y, size, size, 0, 0, 1, 1, tint);
    }

    public void icon(Identifier texture, float x, float y, float size, TextRenderer.ColorFunction colors) {
        iconRegion(texture, Pipelines.ICON, x, y, size, size, 0, 0, 1, 1, colors);
    }

    public void iconRegion(Identifier texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1,
                           TextRenderer.ColorFunction colors) {
        iconRegion(texture, RenderPipelines.GUI_TEXTURED, x, y, width, height, u0, v0, u1, v1, colors);
    }

    private void iconRegion(Identifier texture, RenderPipeline pipeline, float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1, TextRenderer.ColorFunction colors) {
        var loadedTexture = IconTexture.get(texture);
        var setup = TextureSetup.singleTexture(loadedTexture.getTextureView(), loadedTexture.getSampler());
        renderer.submit(pipeline, setup, x, y, width, height, (vertices, pose) -> {
            vertices.addVertexWith2DPose(pose, x, y).setUv(u0, v0).setColor(colors.color(x, y));
            vertices.addVertexWith2DPose(pose, x, y + height).setUv(u0, v1).setColor(colors.color(x, y + height));
            vertices.addVertexWith2DPose(pose, x + width, y + height).setUv(u1, v1).setColor(colors.color(x + width, y + height));
            vertices.addVertexWith2DPose(pose, x + width, y).setUv(u1, v0).setColor(colors.color(x + width, y));
        });
    }

    public void iconRegion(Identifier texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, int tint) {
        var loadedTexture = IconTexture.get(texture);
        quad(RenderPipelines.GUI_TEXTURED, loadedTexture.getTextureView(), loadedTexture.getSampler(),
                x, y, width, height, u0, v0, u1, v1, tint);
    }

    public void region(Identifier texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, int tint) {
        var loadedTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
        quad(RenderPipelines.GUI_TEXTURED, loadedTexture.getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, width, height, u0, v0, u1, v1, tint);
    }

    public void image(GpuTextureView texture, GpuSampler sampler, float x, float y, float width, float height, int tint) {
        quad(RenderPipelines.GUI_TEXTURED, texture, sampler, x, y, width, height, 0, 0, 1, 1, tint);
    }

    public void roundedRegion(Identifier texture, float x, float y, float width, float height, float radius,
                              float u0, float v0, float u1, float v1, int tint) {
        roundedRegion(texture, x, y, width, height, radius, u0, v0, u1, v1, tint, FilterMode.NEAREST);
    }

    private void roundedRegion(Identifier texture, float x, float y, float width, float height, float radius,
                               float u0, float v0, float u1, float v1, int tint, FilterMode filter) {
        if (!Renderer2D.packable(x, y, width, height)) return;
        var loadedTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(filter);
        int encodedWidth = Math.round(width * 16);
        int encodedHeight = Math.round(height * 16);
        int corner = Math.round(SdfPipeline.corner(radius, width, height) * 16);
        renderer.submit(Pipelines.ROUNDED_TEXTURE, TextureSetup.singleTexture(loadedTexture.getTextureView(), sampler),
                x, y, width, height, (vertices, pose) -> {
                    roundedVertex(vertices, pose, x, y, 0, 0, u0, v0, encodedWidth, encodedHeight, corner, tint);
                    roundedVertex(vertices, pose, x, y + height, 0, height, u0, v1, encodedWidth, encodedHeight, corner, tint);
                    roundedVertex(vertices, pose, x + width, y + height, width, height, u1, v1, encodedWidth, encodedHeight, corner, tint);
                    roundedVertex(vertices, pose, x + width, y, width, 0, u1, v0, encodedWidth, encodedHeight, corner, tint);
                });
    }

    private static void roundedVertex(VertexConsumer vertices, Matrix3x2fc pose, float x, float y,
                                      float localX, float localY, float u, float v, int encodedWidth, int encodedHeight,
                                      int radius, int tint) {
        vertices.addVertexWith2DPose(pose, x, y).setColor(tint).setUv(u, v).setUv3(localX, localY)
                .setUv1(encodedWidth, encodedHeight).setUv2(radius, 0);
    }

    void quad(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler, float x, float y, float width, float height,
              float u0, float v0, float u1, float v1, int tint) {
        renderer.submit(pipeline, TextureSetup.singleTexture(texture, sampler), x, y, width, height, (vertices, pose) -> {
            vertices.addVertexWith2DPose(pose, x, y).setUv(u0, v0).setColor(tint);
            vertices.addVertexWith2DPose(pose, x, y + height).setUv(u0, v1).setColor(tint);
            vertices.addVertexWith2DPose(pose, x + width, y + height).setUv(u1, v1).setColor(tint);
            vertices.addVertexWith2DPose(pose, x + width, y).setUv(u1, v0).setColor(tint);
        });
    }
}
