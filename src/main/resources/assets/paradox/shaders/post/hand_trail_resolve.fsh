#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D TrailSampler;
uniform sampler2D MaskSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec4 current = texture(InSampler, texCoord);
    vec4 trail = texture(TrailSampler, texCoord);
    float alpha = trail.a * (1.0 - texture(MaskSampler, texCoord).a);
    fragColor = alpha > current.a ? vec4(trail.rgb, alpha) : current;
}
