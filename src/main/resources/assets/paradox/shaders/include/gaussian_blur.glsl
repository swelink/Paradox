vec4 gaussianBlur(vec2 uv, vec2 direction) {
    vec2 offset = direction / vec2(textureSize(Sampler0, 0));
    vec3 color = texture(Sampler0, uv).rgb * 0.2270270270;
    color += texture(Sampler0, uv + offset * 1.3846153846).rgb * 0.3162162162;
    color += texture(Sampler0, uv - offset * 1.3846153846).rgb * 0.3162162162;
    color += texture(Sampler0, uv + offset * 3.2307692308).rgb * 0.0702702703;
    color += texture(Sampler0, uv - offset * 3.2307692308).rgb * 0.0702702703;
    return vec4(color, 1.0);
}
