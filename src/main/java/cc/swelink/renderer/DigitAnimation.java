package cc.swelink.renderer;

import java.util.ArrayList;
import java.util.List;

public final class DigitAnimation {
    private final float fontSize;
    private String previous;
    private float digitWidth;
    private final List<Glyph> glyphs = new ArrayList<>();

    public DigitAnimation(float fontSize) {
        this.fontSize = fontSize;
    }

    public DigitAnimation(float fontSize, float speed) {
        this(fontSize);
    }

    public void reset() {
        previous = null;
        glyphs.clear();
    }

    public void update(Renderer2D renderer, String text, float delta) {
        if (text.equals(previous)) return;
        if (digitWidth <= 0) {
            for (int digit = 0; digit <= 9; digit++) {
                digitWidth = Math.max(digitWidth, renderer.text.measure(Integer.toString(digit), fontSize).width());
            }
        }
        glyphs.clear();
        float x = 0;
        for (int i = 0; i < text.length(); i++) {
            String character = text.substring(i, i + 1);
            char value = character.charAt(0);
            float textWidth = renderer.text.measure(character, fontSize).width();
            float width = value >= '0' && value <= '9' ? digitWidth : textWidth;
            glyphs.add(new Glyph(character, x, width, (width - textWidth) / 2));
            x += width;
        }
        previous = text;
    }

    public float width() {
        return currentWidth();
    }

    public float currentWidth() {
        return glyphs.isEmpty() ? 0 : glyphs.getLast().x + glyphs.getLast().width;
    }

    public void drawRight(Renderer2D renderer, float right, float y, TextRenderer.ColorFunction colors) {
        draw(renderer, right - currentWidth(), y, colors);
    }

    public void draw(Renderer2D renderer, float x, float y, TextRenderer.ColorFunction colors) {
        for (Glyph glyph : glyphs) {
            renderer.text.drawColored(glyph.text, x + glyph.x + glyph.offset, y, fontSize, colors);
        }
    }

    private record Glyph(String text, float x, float width, float offset) { }
}
