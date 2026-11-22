#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset; };
uniform sampler2D Sampler0;
layout(location=0) in vec2 localPosition;
layout(location=1) flat in vec2 size;
layout(location=2) flat in float radius;
layout(location=3) in vec4 vertexColor;
layout(location=4) in vec2 sceneUv;
layout(location=5) flat in vec4 solidPanel;
layout(location=6) flat in float glassOpacity;
layout(location=7) flat in float edges;
layout(location=8) flat in vec4 cornerRadii;
layout(location=9) flat in vec4 filletRadii;
layout(location=0) out vec4 fragColor;
float box(vec2 p, vec2 center, vec2 extent) {
    vec2 q = abs(p - center) - extent;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
}
float fillet(vec2 p, vec2 corner, vec2 direction) {
    return max(box(p, corner, vec2(radius)), radius - length(p - corner - direction * radius));
}
float concave(vec2 p, vec2 corner, vec2 direction, float size) {
    float overlap = 1.0;
    return max(box(p, corner + direction * (size - overlap) * 0.5, vec2((size + overlap) * 0.5)),
            size - length(p - corner - direction * size));
}
void main() {
    int docked = int(edges + 0.5);
    bool left = (docked & 1) != 0;
    bool top = (docked & 2) != 0;
    bool right = (docked & 4) != 0;
    bool bottom = (docked & 8) != 0;
    bool cornerMode = (docked & 16) != 0;
    vec2 p = localPosition - size * 0.5;
    float corner = cornerMode
            ? (p.x < 0.0 ? (p.y < 0.0 ? cornerRadii.x : cornerRadii.w) : (p.y < 0.0 ? cornerRadii.y : cornerRadii.z))
            : (p.x < 0.0
                ? (p.y < 0.0 ? (left || top ? 0.0 : radius) : (left || bottom ? 0.0 : radius))
                : (p.y < 0.0 ? (right || top ? 0.0 : radius) : (right || bottom ? 0.0 : radius)));
    vec2 q = abs(p) - (size * 0.5 - corner);
    float distance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - corner;
    vec2 local = localPosition;
    if (cornerMode) {
        vec2 end = size;
        int vertical = docked & 15;
        if (filletRadii.x > 0.0) distance = min(distance, concave(local, vec2(0.0), vec2(-1.0, 1.0) * ((vertical & 1) != 0 ? -1.0 : 1.0), filletRadii.x));
        if (filletRadii.y > 0.0) distance = min(distance, concave(local, vec2(end.x, 0.0), vec2(1.0, 1.0) * ((vertical & 2) != 0 ? -1.0 : 1.0), filletRadii.y));
        if (filletRadii.z > 0.0) distance = min(distance, concave(local, end, vec2(1.0, -1.0) * ((vertical & 4) != 0 ? -1.0 : 1.0), filletRadii.z));
        if (filletRadii.w > 0.0) distance = min(distance, concave(local, vec2(0.0, end.y), vec2(-1.0, -1.0) * ((vertical & 8) != 0 ? -1.0 : 1.0), filletRadii.w));
    }
    if (radius > 0.0 && !cornerMode) {
        vec2 end = size;
        if (top) distance = min(distance, min(fillet(local, vec2(0.0), vec2(-1.0, 1.0)), fillet(local, vec2(end.x, 0.0), vec2(1.0, 1.0))));
        if (bottom) distance = min(distance, min(fillet(local, vec2(0.0, end.y), vec2(-1.0, -1.0)), fillet(local, end, vec2(1.0, -1.0))));
        if (left) distance = min(distance, min(fillet(local, vec2(0.0), vec2(1.0, -1.0)), fillet(local, vec2(0.0, end.y), vec2(1.0, 1.0))));
        if (right) distance = min(distance, min(fillet(local, vec2(end.x, 0.0), vec2(-1.0, -1.0)), fillet(local, end, vec2(-1.0, 1.0))));
    }
    float aa = max(fwidth(distance), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, distance);
    vec3 scene = texture(Sampler0, sceneUv).rgb;
    float luminance = dot(scene, vec3(0.2126, 0.7152, 0.0722));
    scene = mix(vec3(luminance), scene, 0.78);
    scene = (scene - 0.5) * 0.85 + 0.5;
    bool hudPanel = cornerMode || solidPanel.w != 0.0;
    float tintStrength = hudPanel ? 0.62 * glassOpacity : 0.80;
    float sceneStrength = hudPanel ? 0.55 : 0.5;
    vec3 glass = mix(scene * sceneStrength, vertexColor.rgb, tintStrength);
    float grain = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    glass += (grain - 0.5) / 255.0;
    if (!cornerMode && (solidPanel.w > 0.0 && localPosition.x < solidPanel.w)
            || (solidPanel.w < 0.0 && localPosition.y < -solidPanel.w)) glass = solidPanel.rgb;
    fragColor = vec4(glass, vertexColor.a * coverage) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}
