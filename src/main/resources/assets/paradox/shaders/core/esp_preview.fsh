#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset; };
uniform sampler2D Sampler0;
layout(location=0) in vec2 localPosition;
layout(location=3) in vec4 vertexColor;
layout(location=0) out vec4 fragColor;
void main() { fragColor = texture(Sampler0, localPosition) * vertexColor * ColorModulator; }
