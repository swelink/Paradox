#version 330
#extension GL_ARB_separate_shader_objects : require
#include <paradox:esp_outline.glsl>
uniform sampler2D InSampler;
layout(std140) uniform EspConfig { vec4 Settings; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    if (Settings.w > 0.5 && Settings.w < 1.5) { fragColor = vec4(0.0); return; }
    fragColor = outlineSource(InSampler, texCoord);
}
