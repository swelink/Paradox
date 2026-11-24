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
    vec4 body = texture(InSampler, texCoord);
    vec4 bloom = texture(GlowSampler, texCoord);
    vec3 glowColor = (bloom.a > 0.0001 ? bloom.rgb / bloom.a : vec3(0.0)) * Tint.rgb;
    vec3 bodyColor = body.a > 0.0001 ? body.rgb * Tint.rgb : glowColor;
    float halo = (1.0 - exp(-bloom.a * Settings.x * 4.0)) * (1.0 - body.a);
    halo *= halo * (3.0 - 2.0 * halo);
    float rim = 0.0;
    if (Settings.z > 0.0 && body.a > 0.0) {
        float inner = body.a;
        for (int i = 0; i < 8; i++) {
            vec2 uv = clamp(texCoord + DIRECTIONS[i] * pixel * Settings.z, pixel * 0.5, vec2(1.0) - pixel * 0.5);
            inner = min(inner, texture(InSampler, uv).a);
        }
        rim = body.a - inner;
    }
    float innerGlow = body.a * (1.0 - smoothstep(0.35, 0.95, bloom.a)) * Settings.x * 0.6;
    float fill = clamp(body.a * Settings.y + innerGlow, 0.0, 1.0);
    vec3 rgb = bodyColor * fill;
    float alpha = fill;
    vec3 rimColor = mix(bodyColor, vec3(1.0), 0.3);
    rgb = rimColor * rim + rgb * (1.0 - rim);
    alpha = rim + alpha * (1.0 - rim);
    rgb += glowColor * halo * (1.0 - alpha);
    alpha += halo * (1.0 - alpha);
    fragColor = vec4(alpha > 0.0001 ? rgb / alpha : vec3(0.0), alpha);
}
