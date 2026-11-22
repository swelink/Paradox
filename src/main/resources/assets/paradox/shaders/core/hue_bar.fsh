#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset; };
layout(location=0) in vec2 localPosition;
layout(location=1) flat in vec2 size;
layout(location=2) flat in float radius;
layout(location=3) in vec4 vertexColor;
layout(location=0) out vec4 fragColor;
#include <paradox:control_shapes.glsl>
void main() {
    vec2 uv = clamp(localPosition / size, 0.0, 1.0);
    float sd = controlDistance(localPosition, size, radius);
    float aa = max(fwidth(sd), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, sd);
    fragColor = vec4(controlHue(uv.x), coverage * vertexColor.a) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}
