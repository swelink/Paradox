#version 330
#extension GL_ARB_separate_shader_objects : require
#include <paradox:fill_wave.glsl>
uniform sampler2D InSampler;
uniform sampler2D SourceSampler;
uniform sampler2D GlowSampler;
uniform sampler2D GlyphsSampler;
layout(std140) uniform EspConfig { vec4 Settings; };
layout(std140) uniform HandColor { vec4 Tint; };
layout(std140) uniform FillWave { vec4 Wave; vec4 PrimaryAccent; vec4 SecondaryAccent; };
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
    if (fill > 0.0) {
        if (Wave.x > 3.5) {
            vec4 effect = glitter(gl_FragCoord.xy, texCoord, Wave.y, PrimaryAccent.rgb, SecondaryAccent.rgb);
            fillColor = effect.rgb;
            fill = mix(fill, original.a, effect.a);
        } else if (Wave.x > 2.5) {
            vec4 effect = matrixRain(GlyphsSampler, gl_FragCoord.xy, vec2(textureSize(InSampler, 0)), Wave.y);
            fillColor = effect.rgb;
            fill = mix(fill, original.a, effect.a);
        } else if (Wave.x > 1.5) {
            fillColor *= marblingWave(gl_FragCoord.xy, vec2(textureSize(InSampler, 0)), Wave.y);
        } else if (Wave.x > 0.5) {
            fillColor = accentWave(texCoord, Wave.y, PrimaryAccent.rgb, SecondaryAccent.rgb);
        }
    }
    rgb += fillColor * fill * (1.0 - alpha);
    alpha += fill * (1.0 - alpha);
    fragColor = vec4(alpha > 0.00001 ? rgb / alpha : vec3(0.0), alpha);
}
