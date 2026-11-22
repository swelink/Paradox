package cc.swelink.renderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;

final class FontAtlas {
    private static final Map<TextRenderer.Font, FontAtlas> FONTS = new EnumMap<>(TextRenderer.Font.class);

    record Glyph(float offsetX, float offsetY, float width, float height,
                 float u0, float v0, float u1, float v1, float advance) {
        boolean visible() {
            return width > 0 && height > 0;
        }
    }

    final int fontSize;
    final float ascent, lineHeight;
    private final String fontName;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Map<Long, Float> kerning = new HashMap<>();
    final int width, height;
    private GpuTexture texture;
    private GpuTextureView view;

    static synchronized FontAtlas forFont(TextRenderer.Font font) {
        return FONTS.computeIfAbsent(font, key -> new FontAtlas(key.resourceName));
    }

    private FontAtlas(String fontName) {
        this.fontName = fontName;
        try (var input = resource(".json");
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject atlas = data.getAsJsonObject("atlas");
            if (!atlas.get("type").getAsString().equals("msdf")
                    || !atlas.get("yOrigin").getAsString().equals("top")
                    || atlas.get("distanceRange").getAsFloat() != 4) {
                throw new IllegalStateException("Unsupported font atlas: " + fontName);
            }
            fontSize = atlas.get("size").getAsInt();
            width = atlas.get("width").getAsInt();
            height = atlas.get("height").getAsInt();
            JsonObject metrics = data.getAsJsonObject("metrics");
            ascent = -metrics.get("ascender").getAsFloat() * fontSize;
            lineHeight = metrics.get("lineHeight").getAsFloat() * fontSize;
            for (var element : data.getAsJsonArray("glyphs")) {
                JsonObject glyph = element.getAsJsonObject();
                int codepoint = glyph.get("unicode").getAsInt();
                float advance = glyph.get("advance").getAsFloat() * fontSize;
                if (!glyph.has("planeBounds")) {
                    glyphs.put(codepoint, new Glyph(0, 0, 0, 0, 0, 0, 0, 0, advance));
                    continue;
                }
                JsonObject plane = glyph.getAsJsonObject("planeBounds");
                JsonObject bounds = glyph.getAsJsonObject("atlasBounds");
                float left = plane.get("left").getAsFloat() * fontSize;
                float top = plane.get("top").getAsFloat() * fontSize;
                glyphs.put(codepoint, new Glyph(left, top,
                        plane.get("right").getAsFloat() * fontSize - left,
                        plane.get("bottom").getAsFloat() * fontSize - top,
                        bounds.get("left").getAsFloat() / width, bounds.get("top").getAsFloat() / height,
                        bounds.get("right").getAsFloat() / width, bounds.get("bottom").getAsFloat() / height,
                        advance));
            }
            for (var element : data.getAsJsonArray("kerning")) {
                JsonObject pair = element.getAsJsonObject();
                kerning.put(pairKey(pair.get("unicode1").getAsInt(), pair.get("unicode2").getAsInt()),
                        pair.get("advance").getAsFloat() * fontSize);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load MSDF font: " + fontName, exception);
        }
    }

    private InputStream resource(String extension) throws IOException {
        var input = FontAtlas.class.getResourceAsStream("/assets/paradox/fonts/" + fontName + "-msdf" + extension);
        if (input == null) throw new IOException("Missing font resource: " + fontName + extension);
        return input;
    }

    private static long pairKey(int first, int second) {
        return (long) first << 32 | (second & 0xFFFFFFFFL);
    }

    float kerning(int first, int second) {
        return kerning.getOrDefault(pairKey(first, second), 0f);
    }

    boolean has(int codepoint) {
        return glyphs.containsKey(codepoint);
    }

    Glyph glyph(int codepoint) {
        return glyphs.getOrDefault(codepoint, glyphs.get((int) '?'));
    }

    int[] pixels() {
        try (var input = resource(".png"); var image = NativeImage.read(input)) {
            return image.getPixels();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read MSDF font: " + fontName, exception);
        }
    }

    GpuTextureView textureView() {
        if (view != null) return view;
        var device = RenderSystem.getDevice();
        texture = device.createTexture("Paradox MSDF " + fontName, 5, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        boolean uploaded = false;
        try (var input = resource(".png"); var image = NativeImage.read(input)) {
            device.createCommandEncoder().writeToTexture(texture, image);
            view = device.createTextureView(texture);
            uploaded = true;
            return view;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot upload MSDF font: " + fontName, exception);
        } finally {
            if (!uploaded) {
                texture.close();
                texture = null;
            }
        }
    }

    static synchronized void shutdown() {
        for (FontAtlas font : FONTS.values()) {
            if (font.view != null) font.view.close();
            if (font.texture != null) font.texture.close();
        }
        FONTS.clear();
    }
}
