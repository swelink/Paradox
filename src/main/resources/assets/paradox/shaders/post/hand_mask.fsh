#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D DepthSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
float covered(vec2 uv) {
    return texture(DepthSampler, uv).r > 0.0 ? 1.0 : 0.0;
}
float bridged(vec2 pixel, vec2 direction) {
    for (int step = 1; step <= 2; step++) {
        vec2 offset = direction * pixel * float(step);
        if (covered(texCoord + offset) > 0.5 && covered(texCoord - offset) > 0.5) return 1.0;
    }
    return 0.0;
}
void main() {
    float coverage = covered(texCoord);
    if (coverage < 0.5) {
        vec2 pixel = 1.0 / vec2(textureSize(DepthSampler, 0));
        coverage = max(max(bridged(pixel, vec2(1.0, 0.0)), bridged(pixel, vec2(0.0, 1.0))),
                max(bridged(pixel, vec2(1.0, 1.0)), bridged(pixel, vec2(1.0, -1.0))));
    }
    fragColor = vec4(vec3(coverage), coverage);
}
