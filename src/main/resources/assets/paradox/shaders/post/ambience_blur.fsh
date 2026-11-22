#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform ParadoxAmbienceInverse { mat4 InverseViewProjection; };
layout(std140) uniform ParadoxAmbienceConfig { vec4 Config; vec4 Extra; };
layout(std140) uniform ParadoxAmbienceDirection { vec4 Direction; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
const int MAX_TAPS = 16;
float sceneDistance(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    if (depth <= 0.0 || depth >= 1.0) return 1.0e6;
    bool zeroToOne = Config.w > 0.5;
    vec4 world = InverseViewProjection * vec4(uv * 2.0 - 1.0, zeroToOne ? depth : depth * 2.0 - 1.0, 1.0);
    if (abs(world.w) < 0.000001) return 1.0e6;
    return length(world.xyz / world.w);
}
float farness(float distance) {
    return smoothstep(Config.x * 0.35, Config.y, distance);
}
void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    int taps = clamp(int(ceil(Config.z)), 1, MAX_TAPS);
    vec2 step = Direction.xy * texel * (Config.z / float(taps));
    bool first = Direction.x > 0.5;
    vec3 sum = vec3(0.0);
    float amount = 0.0;
    float total = 0.0;
    float spread = 0.0;
    for (int i = -taps; i <= taps; i++) {
        vec2 uv = clamp(texCoord + step * float(i), texel * 0.5, 1.0 - texel * 0.5);
        float x = float(i) / float(taps);
        float gaussian = exp(-x * x * 2.5);
        vec4 tap = texture(InSampler, uv);
        float far = farness(sceneDistance(uv));
        float weight = gaussian * mix(0.25, 1.0, far);
        sum += tap.rgb * weight;
        total += weight;
        float value = Extra.w > 0.5 ? clamp(tap.a * 4.0, 0.0, 1.0) : far;
        amount += (first ? value : tap.a) * gaussian;
        spread += gaussian;
    }
    fragColor = vec4(sum / max(total, 0.0001), amount / max(spread, 0.0001));
}
