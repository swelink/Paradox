#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform ParadoxFogInverse { mat4 InverseViewProjection; };
layout(std140) uniform ParadoxFogTint { vec4 Tint; };
layout(std140) uniform ParadoxFogConfig { vec4 Config; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
vec3 unproject(vec2 ndc, float clipDepth, out bool valid) {
    vec4 world = InverseViewProjection * vec4(ndc, clipDepth, 1.0);
    valid = abs(world.w) > 0.000001;
    return valid ? world.xyz / world.w : vec3(0.0);
}
void main() {
    vec4 original = texture(InSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    bool zeroToOne = Config.w > 0.5;
    vec2 ndc = texCoord * 2.0 - 1.0;
    bool valid;
    float amount;
    vec3 color = Tint.rgb;
    if (depth <= 0.0 || depth >= 1.0) {
        vec3 direction = normalize(unproject(ndc, zeroToOne ? 0.5 : 0.0, valid));
        if (!valid) {
            fragColor = original;
            return;
        }
        amount = Config.z;
        color *= mix(1.0, 0.72, smoothstep(0.0, 1.0, direction.y));
    } else {
        vec3 position = unproject(ndc, zeroToOne ? depth : depth * 2.0 - 1.0, valid);
        if (!valid) {
            fragColor = original;
            return;
        }
        float distance = length(position);
        float start = Config.x;
        float end = max(Config.y, start + 0.001);
        float linear = clamp((distance - start) / (end - start), 0.0, 1.0);
        amount = linear * linear * (3.0 - 2.0 * linear);
    }
    fragColor = vec4(mix(original.rgb, color, clamp(amount * Tint.a, 0.0, 1.0)), original.a);
}
