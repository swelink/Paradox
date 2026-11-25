package cc.swelink.gui.screens;

import cc.swelink.renderer.UiTokens;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.SpringAnimation;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ARGB;

final class AnimatedInput {
    private final List<Glyph> glyphs = new ArrayList<>();
    private final List<Glyph> removed = new ArrayList<>();
    private final SpringAnimation scrollMotion = new SpringAnimation();
    private final SpringAnimation placeholderMotion = new SpringAnimation(1);
    private final SpringAnimation selectionStart = new SpringAnimation();
    private final SpringAnimation selectionEnd = new SpringAnimation();
    private final SpringAnimation selectionPresence = new SpringAnimation();
    private final SpringAnimation caretPresence = new SpringAnimation();
    private final SpringAnimation caretPosition = new SpringAnimation();
    private boolean selecting;
    private float[] positions = new float[1];
    private long editedAt;

    void edited() {
        editedAt = System.nanoTime();
    }

    void changed(String value) {
        edited();
        List<String> letters = value.codePoints().mapToObj(Character::toString).toList();
        int prefix = 0;
        while (prefix < glyphs.size() && prefix < letters.size()
                && glyphs.get(prefix).text.equals(letters.get(prefix))) prefix++;
        int suffix = 0;
        while (suffix < glyphs.size() - prefix && suffix < letters.size() - prefix
                && glyphs.get(glyphs.size() - 1 - suffix).text.equals(letters.get(letters.size() - 1 - suffix))) suffix++;
        List<Glyph> next = new ArrayList<>(glyphs.subList(0, prefix));
        for (int i = prefix; i < glyphs.size() - suffix; i++) {
            Glyph glyph = glyphs.get(i);
            removed.add(glyph);
        }
        for (int i = prefix; i < letters.size() - suffix; i++) next.add(new Glyph(letters.get(i)));
        next.addAll(glyphs.subList(glyphs.size() - suffix, glyphs.size()));
        glyphs.clear();
        glyphs.addAll(next);
    }

    float positionOf(int index) {
        return positions[Math.clamp(index, 0, positions.length - 1)];
    }

    float scroll() {
        return scrollMotion.value();
    }

    void draw(Renderer2D renderer, EditBox input, String placeholder, boolean focused,
              float x, float y, float width, float height, int accent, float delta) {
        draw(renderer, input, placeholder, focused, x, y, width, height, accent, delta, null, "");
    }

    void draw(Renderer2D renderer, EditBox input, String placeholder, boolean focused,
              float x, float y, float width, float height, int accent, float delta, int[] colors, String ghost) {
        String value = input.getValue();
        positions = new float[value.length() + 1];
        for (int index = 1; index <= value.length(); index++) {
            positions[index] = Character.isLowSurrogate(value.charAt(index - 1)) || index == value.length()
                    || !Character.isLowSurrogate(value.charAt(index))
                    ? renderer.text.measure(value.substring(0, index), 13).width() : positions[index - 1];
        }
        float centerY = y + height / 2;
        float baseOpacity = renderer.opacity();
        try (var clip = renderer.clips.push(x, y, width, height)) {
            float presence = placeholderMotion.update(value.isEmpty() && !focused ? 1 : 0, 18, delta);
            if (presence > 0.001f) {
                renderer.opacity(baseOpacity * presence);
                label(renderer, placeholder, x + 1, centerY - 2 * (1 - presence), ClickGuiStyle.muted());
                renderer.opacity(baseOpacity);
            }
            int cursor = input.getCursorPosition();
            float caret = renderer.text.measure(value.substring(0, cursor), 13).width();
            float offset = scrollMotion.update(Math.max(0, caret - width + 4), 22, delta);
            String selected = input.getHighlighted();
            boolean hasSelection = focused && !selected.isEmpty();
            if (hasSelection) {
                int end = cursor + selected.length();
                if (end > value.length() || !value.substring(cursor, end).equals(selected)) {
                    end = Math.max(0, cursor - selected.length());
                }
                float other = renderer.text.measure(value.substring(0, end), 13).width();
                float left = Math.min(caret, other);
                float right = Math.max(caret, other);
                if (!selecting) {
                    selectionStart.reset(caret);
                    selectionEnd.reset(caret);
                }
                selectionStart.update(left, 26, delta);
                selectionEnd.update(right, 26, delta);
            } else {
                selectionStart.update(caret, 26, delta);
                selectionEnd.update(caret, 26, delta);
            }
            selecting = hasSelection;
            float selectionAlpha = selectionPresence.update(hasSelection ? 1 : 0, 22, delta);
            if (selectionAlpha > 0.001f) {
                renderer.opacity(baseOpacity * selectionAlpha);
                renderer.sdf.roundedRect(x + 1 + selectionStart.value() - offset, centerY - 9,
                        Math.max(0, selectionEnd.value() - selectionStart.value()), 18, 3, ARGB.color(0x50, accent));
                renderer.opacity(baseOpacity);
            }
            int index = 0;
            for (Glyph glyph : glyphs) {
                glyph.x = positionOf(index);
                float visible = glyph.presence.update(1, 20, delta);
                float rise = glyph.y.update(0, 20, delta);
                renderer.opacity(baseOpacity * visible);
                int color = colors != null && index < colors.length ? colors[index] : ClickGuiStyle.text();
                label(renderer, glyph.text, x + 1 + glyph.x - offset, centerY + rise, color);
                index += glyph.text.length();
            }
            if (!ghost.isEmpty() && cursor == value.length()) {
                renderer.opacity(baseOpacity);
                label(renderer, ghost, x + 1 + positionOf(cursor) - offset, centerY, ClickGuiStyle.muted());
            }
            for (var iterator = removed.iterator(); iterator.hasNext();) {
                Glyph glyph = iterator.next();
                float visible = glyph.presence.update(0, 24, delta);
                if (visible < 0.01f) {
                    iterator.remove();
                    continue;
                }
                float rise = glyph.y.update(-4, 20, delta);
                renderer.opacity(baseOpacity * visible);
                label(renderer, glyph.text, x + 1 + glyph.x - offset, centerY + rise, ClickGuiStyle.text());
            }
            renderer.opacity(baseOpacity);
            float cursorX = caretPosition.update(Math.clamp(caret - offset, 0, Math.max(0, width - 2)), 26, 1, delta);
            float cursorAlpha = caretPresence.update(focused ? 1 : 0, 24, delta);
            if (cursorAlpha > 0.001f) {
                float idle = Math.max(0, (System.nanoTime() - editedAt) * 1e-9f - 0.5f);
                float blink = 0.65f + 0.35f * (float) Math.cos(idle * Math.PI * 2);
                renderer.opacity(baseOpacity * blink * cursorAlpha);
                renderer.sdf.roundedRect(x + 1 + cursorX, centerY - 7, 1, 14, 0.5f, accent);
            }
        } finally {
            renderer.opacity(baseOpacity);
        }
    }

    void reset() {
        glyphs.clear();
        removed.clear();
        scrollMotion.reset(0);
        placeholderMotion.reset(1);
        selectionStart.reset(0);
        selectionEnd.reset(0);
        selectionPresence.reset(0);
        caretPresence.reset(0);
        caretPosition.reset(0);
        selecting = false;
    }

    private static void label(Renderer2D renderer, String text, float x, float centerY, int color) {
        renderer.text.draw(text, x, centerY - renderer.text.lineHeight(UiTokens.TEXT_BODY) / 2, UiTokens.TEXT_BODY, color);
    }

    private static final class Glyph {
        final String text;
        float x;
        final SpringAnimation presence = new SpringAnimation();
        final SpringAnimation y = new SpringAnimation(4);

        Glyph(String text) {
            this.text = text;
        }
    }
}
