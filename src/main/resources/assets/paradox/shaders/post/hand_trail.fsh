#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D HistorySampler;
layout(std140) uniform TrailConfig { vec4 Motion; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec4 current = texture(InSampler, texCoord);
    vec2 uv = vec2(0.5) + (texCoord + Motion.xy - vec2(0.5)) * Motion.z;
    vec4 history = vec4(0.0);
    if (uv.x >= 0.0 && uv.y >= 0.0 && uv.x <= 1.0 && uv.y <= 1.0) {
        history = texture(HistorySampler, uv) * Motion.w;
    }
    fragColor = vec4(current.rgb * current.a, current.a) + history * (1.0 - current.a);
}
