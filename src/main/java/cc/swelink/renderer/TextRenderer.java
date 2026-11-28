package cc.swelink.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.client.gui.render.TextureSetup;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TextRenderer {
    public enum Font {
        INTER("inter-regular"),
        INTER_MEDIUM("inter-medium");

        final String resourceName;

        Font(String resourceName) {
            this.resourceName = resourceName;
        }
    }

    private static final int MAX_CACHED_LAYOUTS = 1024;
    private static final float CAP_HEIGHT = 0.727f;
    private static final Map<Key, Layout> CACHE = new LinkedHashMap<>(128, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Layout> entry) {
            return size() > MAX_CACHED_LAYOUTS;
        }
    };
    private final Renderer2D renderer;
    private final Font font;

    TextRenderer(Renderer2D renderer) {
        this(renderer, Font.INTER);
    }

    TextRenderer(Renderer2D renderer, Font font) {
        this.renderer = renderer;
        this.font = font;
    }

    public TextRenderer withFont(Font font) {
        return this.font == font ? this : new TextRenderer(renderer, font);
    }

    public record Metrics(float width, float height) {
    }

    record Quad(float left, float top, float right, float bottom, float u0, float v0, float u1, float v1) {
    }

    record Bounds(float left, float top, float right, float bottom) {
    }

    record Layout(FontAtlas font, List<Quad> glyphs, Metrics metrics, Bounds bounds) {
    }

    private record Key(String text, float size, float wrap, Font font) {
    }

    public float lineHeight(float size) {
        if (!validSize(size)) {
            return 0;
        }
        var font = FontAtlas.forFont(this.font);
        return font.lineHeight * size / font.fontSize;
    }

    public float ascent(float size) {
        if (!validSize(size)) {
            return 0;
        }
        var font = FontAtlas.forFont(this.font);
        return font.ascent * size / font.fontSize;
    }

    public static boolean canDisplay(Font font, int codepoint) {
        return Character.isWhitespace(codepoint) || FontAtlas.forFont(font).has(codepoint);
    }

    public Metrics measure(String text, float size) {
        return layout(text, size, Float.POSITIVE_INFINITY).metrics;
    }

    public Metrics measureWrapped(String text, float size, float maxWidth) {
        return layout(text, size, maxWidth).metrics;
    }

    public void draw(String text, float x, float y, float size, int color) {
        drawWrapped(text, x, y, size, Float.POSITIVE_INFINITY, color);
    }

    public void draw(String text, float x, float y, float size, int color, boolean shadow) {
        Layout layout = layout(text, size, Float.POSITIVE_INFINITY);
        if (shadow) {
            int shadowAlpha = Math.round((color >>> 24) * 180f / 255);
            int shadowColor = (shadowAlpha << 24) | 0x3C3C3C;
            float offset = size / 27f;
            drawLayout(layout, x + offset, y + offset, shadowColor);
        }
        drawLayout(layout, x, y, color);
    }

    @FunctionalInterface
    public interface ColorFunction {
        int color(float x, float y);
    }

    public void drawColored(String text, float x, float y, float size, ColorFunction colors) {
        Layout layout = layout(text, size, Float.POSITIVE_INFINITY);
        if (layout.glyphs.isEmpty()) {
            return;
        }
        var setup = TextureSetup.singleTexture(layout.font.textureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        Bounds bounds = layout.bounds;
        var shapes = renderer.clips.shapes();
        renderer.submit(Pipelines.TEXT, setup, x + bounds.left, y + bounds.top,
                bounds.right - bounds.left, bounds.bottom - bounds.top, (vertices, pose) -> {
            for (Quad glyph : layout.glyphs) {
                QuadClip.quad(vertices, pose, shapes, x + glyph.left, y + glyph.top, x + glyph.right, y + glyph.bottom,
                        glyph.u0, glyph.v0, glyph.u1, glyph.v1, colors::color);
            }
        });
    }

    public void drawWrapped(String text, float x, float y, float size, float maxWidth, int color) {
        drawLayout(layout(text, size, maxWidth), x, y, color);
    }

    private void drawLayout(Layout layout, float x, float y, int color) {
        if (layout.glyphs.isEmpty() || (color >>> 24) == 0) {
            return;
        }
        var setup = TextureSetup.singleTexture(layout.font.textureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        Bounds bounds = layout.bounds;
        var shapes = renderer.clips.shapes();
        renderer.submit(Pipelines.TEXT, setup, x + bounds.left, y + bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top, (vertices, pose) -> {
            for (Quad glyph : layout.glyphs) {
                QuadClip.quad(vertices, pose, shapes, x + glyph.left, y + glyph.top, x + glyph.right, y + glyph.bottom,
                        glyph.u0, glyph.v0, glyph.u1, glyph.v1, (px, py) -> color);
            }
        });
    }

    public void centered(String text, float centerX, float y, float size, int color) {
        Layout layout = layout(text, size, Float.POSITIVE_INFINITY);
        drawLayout(layout, centerX - layout.metrics.width * 0.5f, y, color);
    }

    public void centeredInBox(String text, float x, float y, float width, float height, float size, int color) {
        Layout layout = layout(text, size, Float.POSITIVE_INFINITY);
        drawLayout(layout, x + (width - layout.metrics.width) * .5f, topInBox(y, height, size), color);
    }

    public void centeredColoredInBox(String text, float x, float y, float width, float height, float size, ColorFunction colors) {
        drawColored(text, x + (width - measure(text, size).width()) * .5f, topInBox(y, height, size), size, colors);
    }

    public float capHeight(float size) {
        return size * CAP_HEIGHT;
    }

    public float topInBox(float y, float height, float size) {
        return y + (height + size * CAP_HEIGHT) * .5f - ascent(size);
    }

    public static synchronized void shutdown() {
        CACHE.clear();
        FontAtlas.shutdown();
    }

    private Layout layout(String text, float size, float wrap) {
        if (text == null || !validSize(size) || !(wrap > 0)) {
            return new Layout(null, List.of(), new Metrics(0, 0), new Bounds(0, 0, 0, 0));
        }
        synchronized (TextRenderer.class) {
            return CACHE.computeIfAbsent(new Key(text, size, wrap, font), TextRenderer::createLayout);
        }
    }

    private static boolean validSize(float size) {
        return size > 0 && Float.isFinite(size);
    }

    private static Layout createLayout(Key key) {
        FontAtlas font = FontAtlas.forFont(key.font);
        List<Quad> quads = new ArrayList<>();
        float scale = key.size / font.fontSize;
        float lineHeight = font.lineHeight * scale;
        float tabWidth = font.glyph(' ').advance() * scale * 4;
        boolean wrapping = Float.isFinite(key.wrap);
        float x = 0;
        float y = 0;
        float width = 0;
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        int previous = -1;
        int[] codePoints = key.text.replace("\r\n", "\n").replace('\r', '\n').codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            int codePoint = codePoints[i];
            if (codePoint == '\n') {
                previous = -1;
                width = Math.max(width, x);
                x = 0;
                y += lineHeight;
                continue;
            }
            if (codePoint == '\t') {
                previous = -1;
                float next = ((float) Math.floor(x / tabWidth) + 1) * tabWidth;
                if (x > 0 && next > key.wrap) {
                    width = Math.max(width, x);
                    x = 0;
                    y += lineHeight;
                } else {
                    x = next;
                }
                continue;
            }
            FontAtlas.Glyph glyph = font.glyph(codePoint);
            boolean space = Character.isWhitespace(codePoint);
            if (wrapping && !space && x > 0 && (i == 0 || Character.isWhitespace(codePoints[i - 1]))) {
                float word = wordWidth(font, codePoints, i, scale);
                if (x + word > key.wrap) {
                    width = Math.max(width, x);
                    x = 0;
                    y += lineHeight;
                    previous = -1;
                }
            }
            float adjustment = font.kerning(previous, codePoint) * scale;
            float advance = glyph.advance() * scale;
            if (x > 0 && x + adjustment + advance > key.wrap) {
                width = Math.max(width, x);
                x = 0;
                y += lineHeight;
                adjustment = 0;
                previous = -1;

                if (space) {
                    continue;
                }
            }
            x += adjustment;
            if (glyph.visible()) {
                Quad quad = createQuad(font, glyph, x, y, scale);
                quads.add(quad);
                left = Math.min(left, quad.left);
                top = Math.min(top, quad.top);
                right = Math.max(right, quad.right);
                bottom = Math.max(bottom, quad.bottom);
            }
            x += advance;
            previous = codePoint;
        }
        Bounds bounds = quads.isEmpty() ? new Bounds(0, 0, 0, 0) : new Bounds(left, top, right, bottom);
        return new Layout(font, List.copyOf(quads), new Metrics(Math.max(width, x), codePoints.length == 0 ? 0 : y + lineHeight), bounds);
    }

    private static Quad createQuad(FontAtlas font, FontAtlas.Glyph glyph, float x, float y, float scale) {
        float left = x + glyph.offsetX() * scale;
        float top = y + (font.ascent + glyph.offsetY()) * scale;
        float right = left + glyph.width() * scale;
        float bottom = top + glyph.height() * scale;
        return new Quad(left, top, right, bottom, glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1());
    }

    private static float wordWidth(FontAtlas font, int[] codePoints, int start, float scale) {
        float width = 0;
        for (int i = start; i < codePoints.length && !Character.isWhitespace(codePoints[i]); i++) {
            var glyph = font.glyph(codePoints[i]);
            width += (glyph.advance() + (i > start ? font.kerning(codePoints[i - 1], codePoints[i]) : 0)) * scale;
        }
        return width;
    }
}
