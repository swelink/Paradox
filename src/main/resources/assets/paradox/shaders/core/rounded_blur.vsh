#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset; };
layout(std140) uniform Projection { mat4 ProjMat; };
layout(location=0) in vec3 Position;
layout(location=1) in vec4 Color;
layout(location=2) in vec2 UV0;
layout(location=3) in vec2 UV3;
layout(location=4) in ivec2 UV1;
layout(location=5) in ivec2 UV2;
layout(location=0) out vec2 localPosition;
layout(location=1) flat out vec2 size;
layout(location=2) flat out float radius;
layout(location=3) out vec4 vertexColor;
layout(location=4) out vec2 sceneUv;
layout(location=5) flat out vec4 solidPanel;
layout(location=6) flat out float glassOpacity;
layout(location=7) flat out float edges;
layout(location=8) flat out vec4 cornerRadii;
layout(location=9) flat out vec4 filletRadii;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPosition = UV0;
    size = UV3;
    radius = float(UV1.x & 2047) / 16.0;
    bool shaped = UV1.x < 0;
    edges = shaped ? float(16 | ((UV1.x >> 10) & 15)) : float((UV1.x >> 11) & 15);
    cornerRadii = shaped ? vec4(UV1.y & 31, (UV1.y >> 5) & 31, (UV1.y >> 10) & 31, UV2.x & 31) * 0.5 : vec4(0.0);
    filletRadii = shaped ? vec4((UV2.x >> 5) & 31, (UV2.x >> 10) & 31, UV1.x & 31, (UV1.x >> 5) & 31) * 0.5 : vec4(0.0);
    vertexColor = Color;
    sceneUv = gl_Position.xy / gl_Position.w * 0.5 + 0.5;
    solidPanel = vec4(vec3((UV2.x >> 8) & 255, UV2.x & 255, UV2.y & 255) / 255.0, float(UV1.y) / 16.0);
    glassOpacity = float((UV2.y >> 8) & 255) / 255.0;
}
