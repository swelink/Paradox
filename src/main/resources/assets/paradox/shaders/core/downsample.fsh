#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D Sampler0;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 ratio = max(abs(vec2(dFdx(texCoord.x), dFdy(texCoord.y))) / texel, vec2(1.0));
    vec2 stepSize = ratio * 0.25 * texel;
    fragColor = (texture(Sampler0, texCoord + stepSize * vec2(-1, -1))
              + texture(Sampler0, texCoord + stepSize * vec2(1, -1))
              + texture(Sampler0, texCoord + stepSize * vec2(-1, 1))
              + texture(Sampler0, texCoord + stepSize * vec2(1, 1))) * 0.25;
    fragColor.a = 1.0;
}
