#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D MaskSampler;
uniform sampler2D HistorySampler;
layout(std140) uniform TrailConfig { vec4 Motion; vec4 Row0; vec4 Row1; vec4 Row2; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    float shape = texture(MaskSampler, texCoord).a;
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 ray = vec3(ndc.x * Row0.w, ndc.y * Row1.w, -1.0);
    vec3 previous = vec3(dot(Row0.xyz, ray), dot(Row1.xyz, ray), dot(Row2.xyz, ray));
    if (previous.z < 0.0) {
        vec2 uv = vec2(previous.x / (-previous.z * Row0.w), previous.y / (-previous.z * Row1.w)) * 0.5 + 0.5;
        if (uv.x >= 0.0 && uv.y >= 0.0 && uv.x <= 1.0 && uv.y <= 1.0) {
            shape = max(shape, texture(HistorySampler, uv).a - max(Motion.z, 1.0 / 255.0));
        }
    }
    fragColor = vec4(1.0, 1.0, 1.0, shape);
}
