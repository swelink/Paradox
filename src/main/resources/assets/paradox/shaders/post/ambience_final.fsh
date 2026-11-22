#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D BlurSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform ParadoxAmbienceInverse { mat4 InverseViewProjection; };
layout(std140) uniform ParadoxAmbienceConfig { vec4 Config; vec4 Extra; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
float farness(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    if (depth <= 0.0 || depth >= 1.0) return 1.0;
    bool zeroToOne = Config.w > 0.5;
    vec4 world = InverseViewProjection * vec4(uv * 2.0 - 1.0, zeroToOne ? depth : depth * 2.0 - 1.0, 1.0);
    if (abs(world.w) < 0.000001) return 1.0;
    return smoothstep(Config.x * 0.35, Config.y, length(world.xyz / world.w));
}
void main() {
    vec4 fogged = texture(InSampler, texCoord);
    vec4 blurred = texture(BlurSampler, texCoord);
    float enabled = step(0.5, Config.z);
    if (Extra.w > 0.5) {
        float covered = max(clamp(fogged.a * 4.0, 0.0, 1.0), clamp(blurred.a * 2.0, 0.0, 1.0));
        float amount = clamp(farness(texCoord) * covered * enabled, 0.0, 1.0);
        fragColor = vec4(mix(fogged.rgb, blurred.rgb, amount), 1.0);
    } else {
        float amount = clamp(max(farness(texCoord), blurred.a) * enabled, 0.0, 1.0);
        fragColor = vec4(mix(fogged.rgb, blurred.rgb, amount), 0.0);
    }
}
