#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    float shape = texture(InSampler, texCoord).a > 0.02 ? 1.0 : 0.0;
    fragColor = vec4(vec3(shape), shape);
}
