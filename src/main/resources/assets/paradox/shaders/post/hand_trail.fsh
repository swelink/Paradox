#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D GlowSampler;
uniform sampler2D HistorySampler;
layout(std140) uniform TrailConfig { vec4 Motion; };
layout(std140) uniform GlowConfig { vec4 Settings; };
layout(std140) uniform GlowTint { vec4 Tint; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    float glow = 1.0 - exp(-texture(GlowSampler, texCoord).a * Settings.x * 4.0);
    glow *= glow * (3.0 - 2.0 * glow);
    vec2 uv = texCoord + Motion.xy;
    float history = 0.0;
    if (uv.x >= 0.0 && uv.y >= 0.0 && uv.x <= 1.0 && uv.y <= 1.0) {
        history = max(texture(HistorySampler, uv).a - max(Motion.z, 1.0 / 255.0), 0.0);
    }
    fragColor = vec4(Tint.rgb, max(glow, history));
}
