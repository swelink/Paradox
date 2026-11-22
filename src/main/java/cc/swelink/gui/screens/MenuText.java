package cc.swelink.gui.screens;

import cc.swelink.renderer.GuiGraphicsAccess;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.TextRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;

import java.util.ArrayList;

public final class MenuText {
    private static final float FONT_SIZE = 10;
    private static final float MIN_SIZE = 9.5f;
    private static final float MAX_SIZE = 10.5f;
    private static final int BASELINE = 7;
    private static GuiGraphicsExtractor graphics;
    private static boolean centered;

    private record Run(String text, int color) {
    }

    private MenuText() {
    }





    public static void centered(boolean value) {
        centered = value;
    }

    public static void enable(GuiGraphicsExtractor extractor) {
        graphics = extractor;
    }

    public static void end() {
        graphics = null;
    }

    public static boolean draw(Font font, FormattedCharSequence text, Matrix3x2fc pose, ScreenRectangle scissor,
                               int x, int y, int color) {
        if (graphics == null) return false;
        var runs = new ArrayList<Run>();
        var whole = new StringBuilder();
        var current = new StringBuilder();
        int[] runColor = {color};
        boolean supported = text.accept((index, style, original) -> {
            int codepoint = Character.isSpaceChar(original) ? ' ' : original;
            if (!TextRenderer.canDisplay(TextRenderer.Font.INTER, codepoint)) return false;
            int styled = style.getColor() == null ? color : ARGB.color(ARGB.alpha(color), style.getColor().getValue());
            if (styled != runColor[0] && !current.isEmpty()) {
                runs.add(new Run(current.toString(), runColor[0]));
                current.setLength(0);
            }
            runColor[0] = styled;
            current.appendCodePoint(codepoint);
            whole.appendCodePoint(codepoint);
            return true;
        });
        if (!supported) return false;
        if (whole.isEmpty()) return true;
        runs.add(new Run(current.toString(), runColor[0]));
        graphics.pose().pushMatrix();
        graphics.pose().set(pose);
        if (scissor != null) GuiGraphicsAccess.of(graphics).paradox$pushScissor(scissor);
        try (var renderer = new Renderer2D(graphics)) {
            float measured = renderer.text.measure(whole.toString(), FONT_SIZE).width();
            float target = font.width(text);
            float size = measured > 0 ? Math.clamp(FONT_SIZE * target / measured, MIN_SIZE, MAX_SIZE) : FONT_SIZE;
            float top = y + BASELINE - renderer.text.ascent(size);
            float left = centered ? x + (target - measured * size / FONT_SIZE) / 2 : x;
            for (var run : runs) {
                renderer.text.draw(run.text(), left, top, size, run.color());
                left += renderer.text.measure(run.text(), size).width();
            }
        } finally {
            if (scissor != null) graphics.disableScissor();
            graphics.pose().popMatrix();
        }
        return true;
    }
}
