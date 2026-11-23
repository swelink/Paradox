package cc.swelink.renderer;

import cc.swelink.Paradox;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

final class Pipelines {
    static final RenderPipeline GUI_FADE_MARKER = gui("gui_fade_marker",
            RenderPipelines.GUI.getShaders().get(ShaderType.VERTEX), RenderPipelines.GUI.getShaders().get(ShaderType.FRAGMENT),
            RenderPipelines.GUI.getVertexFormatBinding(0), RenderPipelines.GUI);
    static final RenderPipeline GUI_BLUR_MARKER = gui("gui_blur_marker",
            RenderPipelines.GUI.getShaders().get(ShaderType.VERTEX), RenderPipelines.GUI.getShaders().get(ShaderType.FRAGMENT),
            RenderPipelines.GUI.getVertexFormatBinding(0), RenderPipelines.GUI);
    static final RenderPipeline GUI_FADE_OVERLAY = gui("gui_fade_overlay",
            Identifier.withDefaultNamespace("core/position_tex_color"), Paradox.id("core/gui_fade"),
            DefaultVertexFormat.POSITION_TEX_COLOR, RenderPipelines.GUI_TEXTURED);
    static final VertexFormat SDF_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV3", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT).build();
    static final RenderPipeline SDF = gui("sdf", Paradox.id("core/sdf"), Paradox.id("core/sdf"), SDF_FORMAT, RenderPipelines.GUI);
    static final RenderPipeline TEXT = gui("text", Identifier.withDefaultNamespace("core/position_tex_color"), Paradox.id("core/text"),
            DefaultVertexFormat.POSITION_TEX_COLOR, RenderPipelines.GUI_TEXTURED);
    static final RenderPipeline ICON = gui("icon", Identifier.withDefaultNamespace("core/position_tex_color"),
            Paradox.id("core/icon"), DefaultVertexFormat.POSITION_TEX_COLOR, RenderPipelines.GUI_TEXTURED);
    static final RenderPipeline ROUNDED_BLUR = gui("rounded_blur", Paradox.id("core/rounded_blur"),
            Paradox.id("core/rounded_blur"), SDF_FORMAT, RenderPipelines.GUI_TEXTURED);
    static final RenderPipeline FROSTED_GLASS = gui("frosted_glass", Paradox.id("core/rounded_blur"),
            Paradox.id("core/frosted_glass"), SDF_FORMAT, RenderPipelines.GUI_TEXTURED);
    static final RenderPipeline ROUNDED_TEXTURE = gui("rounded_texture", Paradox.id("core/rounded_texture"),
            Paradox.id("core/rounded_blur"), SDF_FORMAT, RenderPipelines.GUI_TEXTURED);
    static final RenderPipeline COLOR_FIELD = gui("color_field", Paradox.id("core/rounded_texture"),
            Paradox.id("core/color_field"), SDF_FORMAT, RenderPipelines.GUI);
    static final RenderPipeline HUE_BAR = gui("hue_bar", Paradox.id("core/rounded_texture"),
            Paradox.id("core/hue_bar"), SDF_FORMAT, RenderPipelines.GUI);

    private static RenderPipeline gui(String name, Identifier vertex, Identifier fragment, VertexFormat format, RenderPipeline base) {
        var builder = RenderPipeline.builder().withLocation(Paradox.id("pipeline/" + name))
                .withVertexShader(vertex)
                .withFragmentShader(fragment).withVertexBinding(0, format)
                .withPrimitiveTopology(PrimitiveTopology.QUADS).withCull(false)
                .withDepthStencilState(Optional.ofNullable(base.getDepthStencilState()))
                .withColorTargetState(base.getColorTargetStates().getFirst());
        for (var layout : base.getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }
        return builder.build();
    }

    private Pipelines() {
    }
}
