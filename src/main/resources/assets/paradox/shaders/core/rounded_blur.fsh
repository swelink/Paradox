#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset; };
uniform sampler2D Sampler0;
layout(location=0) in vec2 localPosition;
layout(location=1) flat in vec2 size;
layout(location=2) flat in float radius;
layout(location=3) in vec4 vertexColor;
layout(location=4) in vec2 sceneUv;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 q = abs(localPosition - size * 0.5) - (size * 0.5 - radius);
    float distance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(distance), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, distance);
    fragColor = texture(Sampler0, sceneUv) * vertexColor * ColorModulator;
    fragColor.a *= coverage;
    if (fragColor.a < 0.001) discard;
}
