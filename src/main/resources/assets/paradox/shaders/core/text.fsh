#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
uniform sampler2D Sampler0;
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vertexColor * ColorModulator;
    vec3 distances = texture(Sampler0, texCoord0).rgb;
    float distance = max(min(distances.r, distances.g), min(max(distances.r, distances.g), distances.b));
    vec2 unitRange = vec2(4.0) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / max(fwidth(texCoord0), vec2(1e-6));
    float screenRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float coverage = clamp(screenRange * (distance - 0.5) + 0.5, 0.0, 1.0);
    fragColor.a *= coverage;
    if (fragColor.a <= 0.0) discard;
}
