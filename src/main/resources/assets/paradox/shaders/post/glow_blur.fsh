#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform BlurKernel { vec4 BlurSamples[15]; };
layout(std140) uniform BlurDirection { vec4 Direction; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 pixel = 1.0 / vec2(textureSize(InSampler, 0));
    vec2 axis = Direction.xy * pixel;
    vec4 color = texture(InSampler, texCoord);
    for (int i = 1; i <= 14; i++) {
        vec2 delta = axis * BlurSamples[i].x;
        color += (texture(InSampler, clamp(texCoord + delta, pixel * 0.5, vec2(1.0) - pixel * 0.5))
                + texture(InSampler, clamp(texCoord - delta, pixel * 0.5, vec2(1.0) - pixel * 0.5))) * BlurSamples[i].y;
    }
    fragColor = color / BlurSamples[0].x;
}
