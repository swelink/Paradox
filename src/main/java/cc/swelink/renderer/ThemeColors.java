package cc.swelink.renderer;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.client.ClickGuiModule;

public final class ThemeColors {
    public static Theme theme() {
        return Paradox.MODULES.get(ClickGuiModule.class).theme();
    }

    public static int primary() {
        return theme().primary;
    }

    public static int secondary() {
        return theme().secondary;
    }

    public static int hudPanel() {
        return theme().hudPanel;
    }

    public static int hudHeader() {
        return theme().hudHeader;
    }

    public static int text() {
        return theme().text;
    }

    public static int muted() {
        return theme().muted;
    }

    public static float wavePhase() {
        return (System.nanoTime() % 4_000_000_000L) / 4_000_000_000f * (float) (Math.PI * 2);
    }

    public static int wave(int first, int second, float y, float phase) {
        float amount = (float) (0.5 - 0.5 * Math.cos(y / 160f * Math.PI * 2 - phase));
        int alpha = Math.round((first >>> 24) + ((second >>> 24) - (first >>> 24)) * amount);
        int red = Math.round((first >> 16 & 255) + ((second >> 16 & 255) - (first >> 16 & 255)) * amount);
        int green = Math.round((first >> 8 & 255) + ((second >> 8 & 255) - (first >> 8 & 255)) * amount);
        int blue = Math.round((first & 255) + ((second & 255) - (first & 255)) * amount);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private ThemeColors() {
    }
}
