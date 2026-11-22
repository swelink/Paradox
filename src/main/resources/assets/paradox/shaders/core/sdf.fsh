#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset; };
layout(location=0) in vec2 localPosition;
layout(location=1) flat in vec2 size;
layout(location=2) flat in vec4 params;
layout(location=3) in vec4 vertexColor;
layout(location=0) out vec4 fragColor;
vec2 gaussianCdf(vec2 value) {
    vec2 t = 1.0 / (1.0 + 0.2316419 * abs(value));
    vec2 density = 0.39894228 * exp(-0.5 * value * value);
    vec2 tail = density * t * (0.31938153 + t * (-0.35656378 + t *
            (1.78147794 + t * (-1.82125598 + t * 1.33027443))));
    return mix(tail, 1.0 - tail, step(vec2(0.0), value));
}
float cornerInset(float radius, float side) {
    float corner = max(radius - side, 0.0);
    return radius - sqrt(max(radius * radius - corner * corner, 0.0));
}
float roundedSlice(vec2 point, vec2 extent, vec4 radii, float sigma, float x) {
    bool leftHalf = x < extent.x * 0.5;
    float side = leftHalf ? x : extent.x - x;
    float top = cornerInset(leftHalf ? radii.x : radii.y, side);
    float bottom = cornerInset(leftHalf ? radii.w : radii.z, side);
    vec2 vertical = gaussianCdf(vec2(point.y - top, point.y - extent.y + bottom) / sigma);
    float offset = (x - point.x) / sigma;
    return (vertical.x - vertical.y) * 0.39894228 * exp(-0.5 * offset * offset) / sigma;
}
float roundedGaussian(vec2 point, vec2 extent, vec4 radii, float sigma) {
    if (max(max(radii.x, radii.y), max(radii.z, radii.w)) == 0.0) {
        vec2 integral = gaussianCdf(point / sigma) - gaussianCdf((point - extent) / sigma);
        return clamp(integral.x * integral.y, 0.0, 1.0);
    }
    const float nodes[8] = float[8](0.09501251, 0.28160355, 0.45801678, 0.61787624,
            0.75540441, 0.86563120, 0.94457502, 0.98940093);
    const float weights[8] = float[8](0.18945061, 0.18260342, 0.16915652, 0.14959599,
            0.12462897, 0.09515851, 0.06225352, 0.02715246);
    float left = max(0.0, point.x - sigma * 4.0);
    float right = min(extent.x, point.x + sigma * 4.0);
    if (right <= left) return 0.0;
    float center = (left + right) * 0.5;
    float halfWidth = (right - left) * 0.5;
    float integral = 0.0;
    for (int i = 0; i < 8; i++) {
        float offset = halfWidth * nodes[i];
        integral += weights[i] * (roundedSlice(point, extent, radii, sigma, center - offset)
                + roundedSlice(point, extent, radii, sigma, center + offset));
    }
    return clamp(integral * halfWidth, 0.0, 1.0);
}
float fillet(vec2 p, vec2 corner, vec2 direction, float radius, bool horizontal, float overhang) {
    vec2 center = horizontal ? corner + vec2(0.0, direction.y * (radius - overhang) * 0.5)
            : corner + vec2(direction.x * (radius - overhang) * 0.5, 0.0);
    vec2 extent = horizontal ? vec2(radius, (radius + overhang) * 0.5) : vec2((radius + overhang) * 0.5, radius);
    vec2 q = abs(p - center) - extent;
    float box = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
    return max(box, radius - length(p - corner - direction * radius));
}
float dockedDistance(vec2 p, vec2 extent, float radius, int edges, float overhang) {
    bool left = (edges & 1) != 0;
    bool top = (edges & 2) != 0;
    bool right = (edges & 4) != 0;
    bool bottom = (edges & 8) != 0;
    vec2 centered = p - extent * 0.5;
    bool square = centered.x < 0.0 ? (centered.y < 0.0 ? left || top : left || bottom)
            : (centered.y < 0.0 ? right || top : right || bottom);
    float corner = square ? 0.0 : radius;
    vec2 low = vec2(left ? -overhang : 0.0, top ? -overhang : 0.0);
    vec2 high = extent + vec2(right ? overhang : 0.0, bottom ? overhang : 0.0);
    vec2 q = abs(p - (low + high) * 0.5) - ((high - low) * 0.5 - corner);
    float distance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - corner;
    if (radius <= 0.0) return distance;
    if (top) distance = min(distance, min(fillet(p, vec2(0.0), vec2(-1.0, 1.0), radius, true, overhang),
            fillet(p, vec2(extent.x, 0.0), vec2(1.0, 1.0), radius, true, overhang)));
    if (bottom) distance = min(distance, min(fillet(p, vec2(0.0, extent.y), vec2(-1.0, -1.0), radius, true, overhang),
            fillet(p, extent, vec2(1.0, -1.0), radius, true, overhang)));
    if (left) distance = min(distance, min(fillet(p, vec2(0.0), vec2(1.0, -1.0), radius, false, overhang),
            fillet(p, vec2(0.0, extent.y), vec2(1.0, 1.0), radius, false, overhang)));
    if (right) distance = min(distance, min(fillet(p, vec2(extent.x, 0.0), vec2(-1.0, -1.0), radius, false, overhang),
            fillet(p, extent, vec2(-1.0, 1.0), radius, false, overhang)));
    return distance;
}
void main() {
    int kind = int(params.w + 0.5);
    if ((kind & 15) == 7) {
        float docked = dockedDistance(localPosition, size, params.x, kind >> 4, 0.0);
        float fillAa = max(fwidth(docked), 0.0001);
        fragColor = vertexColor * ColorModulator;
        fragColor.a *= 1.0 - smoothstep(-fillAa * 0.5, fillAa * 0.5, docked);
        if (fragColor.a < 0.001) discard;
        return;
    }
    if ((kind & 15) == 6) {
        int edges = kind >> 4;
        float docked = dockedDistance(localPosition, size, params.x, edges, params.x * 2.0 + 2.0);
        float edgeAa = max(fwidth(docked), 0.0001);
        float stroke = smoothstep(-edgeAa * 0.5, edgeAa * 0.5, docked)
                * (1.0 - smoothstep(params.y - edgeAa * 0.5, params.y + edgeAa * 0.5, docked));
        fragColor = vertexColor * ColorModulator;
        fragColor.a *= stroke;
        if (fragColor.a < 0.001) discard;
        return;
    }
    vec2 q=abs(localPosition-size*0.5)-(size*0.5-params.x);
    float distance=length(max(q,0.0))+min(max(q.x,q.y),0.0)-params.x;
    float aa=max(fwidth(distance),0.0001);
    bool cornerShadow = params.w == 8.0;
    bool backdropShadow = params.w == 5.0 || cornerShadow;
    if(params.w==3.0) aa*=0.35;
    float coverage=1.0-smoothstep(-aa*0.5,aa*0.5,distance);
    if(params.w==4.0) coverage=1.0-coverage;
    if(params.w==1.0) coverage*=smoothstep(-params.y-aa*0.5,-params.y+aa*0.5,distance);
    bool blurredFill = params.w == 2.0 || backdropShadow;
    if (blurredFill) {
        float sigma = max(params.z, 0.001);
        vec2 point = localPosition - (backdropShadow ? vec2(0.0, sigma * 0.45) : vec2(0.0));
        vec4 radii = vec4(params.x);
        if (cornerShadow) {
            int first = int(params.x * 16.0 + 0.5);
            int second = int(params.y * 16.0 + 0.5);
            radii = vec4(first & 31, (first >> 5) & 31, (first >> 10) & 31, second & 31) * 0.5;
            radii = min(radii, vec4(min(size.x, size.y) * 0.5));
        }
        coverage = roundedGaussian(point, size, radii, sigma);
    }
    fragColor=vertexColor*ColorModulator;
    fragColor.a*=coverage;
    if(fragColor.a<0.001) discard;
}
