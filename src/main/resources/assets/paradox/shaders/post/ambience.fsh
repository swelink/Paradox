#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform ParadoxAmbienceInverse { mat4 InverseViewProjection; };
layout(std140) uniform ParadoxAmbienceConfig { vec4 Config; vec4 Extra; };
layout(std140) uniform ParadoxAmbienceTint { vec4 Tint; vec4 Camera; vec4 Secondary; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
const int STEPS = 6;
const mat3 OCTAVE = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float hash(vec3 p) {
    uvec3 v = uvec3(ivec3(p)) * 1664525u + 1013904223u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return float(v.x) * (1.0 / 4294967295.0);
}
float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
float fbm(vec3 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += noise(p) * amplitude;
        p = OCTAVE * p * 2.03 + vec3(17.1, 3.7, 9.2);
        amplitude *= 0.5;
    }
    return value / 0.9375;
}
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
    bool sky = depth <= 0.0 || depth >= 1.0;
    bool valid;
    vec3 position = unproject(ndc, sky ? (zeroToOne ? 0.5 : 0.0) : (zeroToOne ? depth : depth * 2.0 - 1.0), valid);
    if (!valid) {
        fragColor = original;
        return;
    }
    vec3 direction = normalize(position);
    float distance = sky ? 1.0e6 : length(position);
    float start = Config.x;
    float end = max(Config.y, start + 0.001);
    float fog = sky ? 1.0 : smoothstep(start, end, distance);
    float marchEnd = min(distance, end * 1.6);
    float marchStart = min(start * 0.5, marchEnd);
    float time = Extra.z;
    vec3 drift = vec3(time * 0.035, time * 0.012, time * 0.022);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float density = 0.0;
    for (int i = 0; i < STEPS; i++) {
        float t = mix(marchStart, marchEnd, (float(i) + jitter) / float(STEPS));
        density += fbm((Camera.xyz + direction * t) * 0.045 + drift);
    }
    float cloud = smoothstep(0.3, 0.8, density / float(STEPS));
    float clouds = Extra.y;
    float amount = fog * mix(1.0, 0.45 + cloud * 0.9, clouds);
    amount += (1.0 - fog) * cloud * clouds * 0.18 * smoothstep(0.0, start, distance);
    vec3 color = mix(Tint.rgb, Secondary.rgb, cloud * 0.6) * (0.9 + cloud * 0.18);
    vec3 result = mix(original.rgb, color, clamp(amount * Extra.x, 0.0, 1.0));
    if (Extra.w > 0.5) {
        fragColor = vec4(mix(original.rgb, result, clamp(original.a * 4.0, 0.0, 1.0)), original.a);
    } else {
        fragColor = vec4(result, 1.0);
    }
}
