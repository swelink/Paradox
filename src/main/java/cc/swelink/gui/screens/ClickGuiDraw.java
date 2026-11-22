package cc.swelink.gui.screens;

import cc.swelink.renderer.ClipStack;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.TextRenderer;

final class ClickGuiDraw {
    private ClickGuiDraw() {
    }

    static void drawExpandIcon(Renderer2D renderer, float x, float y, boolean expanded) {
        drawExpandIcon(renderer, x, y, expanded, ClickGuiStyle.text());
    }

    static void drawExpandIcon(Renderer2D renderer, float x, float y, boolean expanded, int color) {
        float direction = expanded ? -1 : 1;
        renderer.sdf.line(x - 3, y - direction, x, y + 2 * direction, 1, color);
        renderer.sdf.line(x, y + 2 * direction, x + 3, y - direction, 1, color);
    }

    static int mix(int from, int to, float amount) {
        amount = Math.clamp(amount, 0, 1);
        int color = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = (from >>> shift) & 255;
            int b = (to >>> shift) & 255;
            color |= Math.round(a + (b - a) * amount) << shift;
        }
        return color;
    }

    static String fitLabel(Renderer2D renderer, String text, float size, float width) {
        return fitLabel(renderer.text, text, size, width);
    }

    static String fitLabel(TextRenderer renderer, String text, float size, float width) {
        if (renderer.measure(text, size).width() <= width) return text;
        String suffix = "…";
        if (renderer.measure(suffix, size).width() > width) return "";
        int end = text.length();
        while (end > 0 && renderer.measure(text.substring(0, end) + suffix, size).width() > width) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end) + suffix;
    }

    static void label(Renderer2D renderer, String text, float x, float centerY, int color) {
        label(renderer, text, x, centerY, ClickGuiStyle.FONT_SIZE, color);
    }

    static void label(Renderer2D renderer, String text, float x, float centerY, float size, int color) {
        renderer.text.draw(text, x, centerY - renderer.text.lineHeight(size) / 2, size, color);
    }

    static void label(Renderer2D renderer, String text, float x, float centerY, float size,
                      TextRenderer.ColorFunction colors) {
        renderer.text.drawColored(text, x, centerY - renderer.text.lineHeight(size) / 2, size, colors);
    }

    static ClipStack.Scope clip(Renderer2D renderer, float x, float y, float width, float height) {
        return renderer.clips.push(x, y, Math.max(0, width), Math.max(0, height));
    }

    static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
