#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D GlowSampler;
layout(std140) uniform GlowConfig { vec4 Settings; };
layout(std140) uniform GlowTint { vec4 Tint; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
const vec2 DIRECTIONS[8] = vec2[](vec2(1.0, 0.0), vec2(-1.0, 0.0), vec2(0.0, 1.0), vec2(0.0, -1.0),
        vec2(0.7071, 0.7071), vec2(-0.7071, 0.7071), vec2(0.7071, -0.7071), vec2(-0.7071, -0.7071));
void main() {
    vec2 pixel = 1.0 / vec2(textureSize(InSampler, 0));
    float body = texture(InSampler, texCoord).a > 0.02 ? 1.0 : 0.0;
    vec4 bloom = texture(GlowSampler, texCoord);
    vec3 color = Tint.rgb;
    float halo = (1.0 - exp(-bloom.a * Settings.x * 4.0)) * (1.0 - body);
    halo *= halo * (3.0 - 2.0 * halo);
    float rim = 0.0;
    if (Settings.z > 0.0 && body > 0.0) {
        float inner = 1.0;
        for (int i = 0; i < 8; i++) {
            vec2 uv = clamp(texCoord + DIRECTIONS[i] * pixel * Settings.z, pixel * 0.5, vec2(1.0) - pixel * 0.5);
            inner = min(inner, texture(InSampler, uv).a > 0.02 ? 1.0 : 0.0);
        }
        rim = 1.0 - inner;
    }
    float fill = clamp(Settings.y, 0.0, 1.0) * body;
    vec3 rgb = color * fill;
    float alpha = fill;
    vec3 rimColor = mix(color, vec3(1.0), 0.3);
    rgb = rimColor * rim + rgb * (1.0 - rim);
    alpha = rim + alpha * (1.0 - rim);
    rgb += color * halo * (1.0 - alpha);
    alpha += halo * (1.0 - alpha);
    fragColor = vec4(alpha > 0.0001 ? rgb / alpha : vec3(0.0), alpha);
}
