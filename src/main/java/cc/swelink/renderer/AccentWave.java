package cc.swelink.renderer;

import net.minecraft.util.ARGB;

public final class AccentWave {
    private AccentWave() {
    }

    public static TextRenderer.ColorFunction text() {
        return text(ThemeColors.primary(), 1);
    }

    public static TextRenderer.ColorFunction text(int base, float amount) {
        int color = ARGB.srgbLerp(amount, base, ThemeColors.primary());
        return (x, y) -> color;
    }

    public static TextRenderer.ColorFunction pulse() {
        float amount = (float) (0.5 - 0.5 * Math.cos(ThemeColors.wavePhase()));
        int color = ARGB.srgbLerp(amount, ThemeColors.primary(), ThemeColors.secondary());
        return (x, y) -> color;
    }

    public static TextRenderer.ColorFunction wave(int base, float amount) {
        if (amount <= 0.001f) return (x, y) -> base;
        int first = ThemeColors.primary();
        int second = ThemeColors.secondary();
        float phase = ThemeColors.wavePhase();
        return (x, y) -> ARGB.srgbLerp(amount, base, ThemeColors.wave(first, second, x, phase));
    }
}
