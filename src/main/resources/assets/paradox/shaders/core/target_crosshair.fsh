#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset; };
uniform sampler2D Sampler0;
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;
float maskAt(vec2 uv) {
    vec4 source = texture(Sampler0, uv);
    return max(source.r, max(source.g, source.b)) * source.a;
}
void main() {
    vec2 offset = max(vec2(3.0) / vec2(textureSize(Sampler0, 0)), fwidth(texCoord0) * 1.2);
    float center = maskAt(texCoord0);
    float glow = center * 0.25;
    glow += (maskAt(texCoord0 + vec2(offset.x, 0.0))
            + maskAt(texCoord0 - vec2(offset.x, 0.0))
            + maskAt(texCoord0 + vec2(0.0, offset.y))
            + maskAt(texCoord0 - vec2(0.0, offset.y))) * 0.125;
    glow += (maskAt(texCoord0 + offset)
            + maskAt(texCoord0 - offset)
            + maskAt(texCoord0 + vec2(offset.x, -offset.y))
            + maskAt(texCoord0 + vec2(-offset.x, offset.y))) * 0.0625;
    float opacity = mix(glow, center, smoothstep(0.35, 0.9, center));
    fragColor = vertexColor * ColorModulator;
    fragColor.a *= opacity;
    if (fragColor.a < 0.001) discard;
}
