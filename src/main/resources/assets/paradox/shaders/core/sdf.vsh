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
layout(location=2) flat out vec4 params;
layout(location=3) out vec4 vertexColor;
void main() {
    gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
    localPosition=UV0; size=UV3;
    params=vec4(vec2(UV1)/16.0,float(UV2.x)/16.0,float(UV2.y));
    vertexColor=Color;
}
