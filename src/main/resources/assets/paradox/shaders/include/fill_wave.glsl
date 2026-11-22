vec3 accentWave(vec2 texCoord, float time, vec3 primary, vec3 secondary) {
    float blend = 0.5 + 0.5 * sin(texCoord.x * 6.2831853 - time * 1.5);
    return mix(primary, secondary, blend);
}

float marblingField(vec2 uv, float time) {
    for (int index = 1; index < 10; ++index) {
        float i = float(index);
        uv.x += 0.6 / i * cos(i * 2.5 * uv.y + time);
        uv.y += 0.6 / i * cos(i * 1.5 * uv.x + time);
    }
    return min(0.1 / max(abs(sin(time - uv.y - uv.x)), 0.0001), 1.0);
}

float marblingWave(vec2 fragCoord, vec2 resolution, float time) {
    return marblingField((2.0 * fragCoord - resolution) / max(min(resolution.x, resolution.y), 1.0), time);
}

float fillHash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float fillHash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec4 matrixRain(sampler2D glyphs, vec2 fragCoord, vec2 resolution, float time) {
    float cellHeight = max(floor(resolution.y / 48.0), 12.0);
    vec2 cellSize = vec2(floor(cellHeight * 0.6), cellHeight);
    vec2 pixel = vec2(fragCoord.x, resolution.y - fragCoord.y);
    vec2 cell = floor(pixel / cellSize);
    vec2 local = (pixel - cell * cellSize) / cellSize;
    float rows = ceil(resolution.y / cellSize.y);
    float lit = 0.0;
    float head = 0.0;
    for (int layer = 0; layer < 2; ++layer) {
        float seed = float(layer) * 17.31;
        float speed = 8.0 + fillHash12(vec2(cell.x, 1.7 + seed)) * 16.0;
        float trail = 4.0 + floor(fillHash12(vec2(cell.x, 9.1 + seed)) * rows * 0.6);
        float cycle = rows + trail + floor(fillHash12(vec2(cell.x, 5.9 + seed)) * rows);
        float position = floor(mod(time * speed + fillHash12(vec2(cell.x, 4.3 + seed)) * cycle, cycle));
        float behind = position - cell.y;
        if (behind >= 0.0 && behind < trail) {
            lit = 1.0;
            if (behind < 0.5) {
                head = 1.0;
            }
        }
    }
    if (lit < 0.5) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    float changeRate = fillHash12(cell + 2.2) > 0.85 ? 10.0 : 0.4;
    float symbol = floor(time * changeRate + fillHash12(cell) * 10.0);
    float index = floor(fillHash13(vec3(cell, symbol)) * 94.0);
    vec2 atlasCell = vec2(mod(index, 16.0), floor(index / 16.0));
    vec2 inset = vec2(0.5) + (local - vec2(0.5)) * vec2(1.0, 0.9);
    float coverage = texture(glyphs, (atlasCell + inset) * vec2(32.0, 48.0) / vec2(512.0, 288.0)).a;
    float bold = step(0.7, fillHash13(vec3(cell, symbol + 3.0)));
    vec3 green = mix(vec3(0.0, 0.62, 0.0), vec3(0.2, 1.0, 0.2), bold);
    vec3 color = mix(green, vec3(1.0), head);
    return vec4(color * coverage, coverage);
}

vec4 glitter(vec2 fragCoord, vec2 texCoord, float time, vec3 primary, vec3 secondary) {
    vec3 base = accentWave(texCoord, time * 0.5, primary, secondary) * 0.55;
    vec3 sparkleColor = vec3(0.0);
    float sparkle = 0.0;
    for (int layer = 0; layer < 2; ++layer) {
        float size = layer == 0 ? 9.0 : 17.0;
        float seed = float(layer) * 31.7;
        vec2 cell = floor(fragCoord / size);
        vec2 local = fragCoord / size - cell;
        float h = fillHash12(cell + seed);
        vec2 center = 0.25 + 0.5 * vec2(fillHash12(cell + 7.3 + seed), fillHash12(cell + 13.1 + seed));
        float twinkle = pow(max(sin(time * (1.5 + h * 3.5) + h * 40.0), 0.0), 10.0);
        vec2 offset = (local - center) * size;
        float reach = size * 0.45 * twinkle;
        float core = exp(-dot(offset, offset) * 0.5);
        float rayX = exp(-abs(offset.y) * 2.5) * max(1.0 - abs(offset.x) / max(reach, 0.001), 0.0);
        float rayY = exp(-abs(offset.x) * 2.5) * max(1.0 - abs(offset.y) / max(reach, 0.001), 0.0);
        float amount = twinkle * (core + 0.7 * max(rayX, rayY));
        sparkleColor += mix(vec3(1.0), mix(primary, secondary, h), 0.35) * amount;
        sparkle += amount;
    }
    float dust = step(0.985, fillHash13(vec3(floor(fragCoord / 2.0), floor(time * 6.0)))) * 0.6;
    sparkleColor += vec3(1.0) * dust;
    sparkle += dust;
    return vec4(base + sparkleColor, clamp(sparkle, 0.0, 1.0));
}
