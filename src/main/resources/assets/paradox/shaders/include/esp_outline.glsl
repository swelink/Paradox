layout(std140) uniform OutlineKernel { vec4 OutlineSamples[97]; };
vec4 outlineSource(sampler2D image, vec2 uv) {
    vec2 pixel = 1.0 / vec2(textureSize(image, 0));
    vec4 center = texture(image, uv);
    float outer = center.a, inner = center.a;
    vec3 color = center.rgb;
    for (int i = 1; i <= int(OutlineSamples[0].x); i++) {
        vec3 sampleData = OutlineSamples[i].xyz;
        vec2 sampleUv = clamp(uv + sampleData.xy * pixel, pixel * 0.5, vec2(1.0) - pixel * 0.5);
        vec4 sampleColor = texture(image, sampleUv);
        float alpha = sampleColor.a * sampleData.z;
        if (alpha > outer) { outer = alpha; color = sampleColor.rgb; }
        inner = min(inner, mix(center.a, sampleColor.a, sampleData.z));
    }
    float source = max(outer - inner, 0.0);
    return vec4(color * source, source);
}
