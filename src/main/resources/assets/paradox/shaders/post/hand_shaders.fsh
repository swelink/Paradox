#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D SourceSampler;
uniform sampler2D GlowSampler;
layout(std140) uniform EspConfig { vec4 Settings; };
layout(std140) uniform HandColor { vec4 Tint; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec4 original = texture(InSampler, texCoord);
    float fill = Settings.w < 0.5 ? 0.0 : original.a * Settings.y;
    vec4 source = texture(SourceSampler, texCoord);
    vec4 bloom = texture(GlowSampler, texCoord);
    float halo = 1.0 - exp(-bloom.a * 2.5);
    float core = source.a * 0.85;
    vec3 haloColor = bloom.a > 0.00001 ? bloom.rgb / bloom.a : vec3(0.0);
    vec3 coreColor = source.a > 0.00001 ? source.rgb / source.a : vec3(0.0);
    if (Tint.a < 0.5) {
        haloColor = vec3(1.0);
        coreColor = vec3(1.0);
    }
    vec3 rgb = haloColor * halo;
    rgb = coreColor * core + rgb * (1.0 - core);
    float alpha = core + halo * (1.0 - core);
    rgb *= Tint.rgb;
    vec3 fillColor = (Tint.a < 0.5 ? vec3(1.0) : original.rgb) * Tint.rgb;
    rgb += fillColor * fill * (1.0 - alpha);
    alpha += fill * (1.0 - alpha);
    fragColor = vec4(alpha > 0.00001 ? rgb / alpha : vec3(0.0), alpha);
}
