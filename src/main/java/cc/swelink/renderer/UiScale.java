package cc.swelink.renderer;

import net.minecraft.client.Minecraft;

public final class UiScale {
    public static final float REFERENCE_WIDTH = 960;
    public static final float REFERENCE_HEIGHT = 540;

    private UiScale() {
    }

    public static float of(Minecraft client) {
        var window = client.getWindow();
        float pixels = Math.min(window.getWidth() / REFERENCE_WIDTH, window.getHeight() / REFERENCE_HEIGHT);
        return Math.max(pixels, 0.0001f) / window.getGuiScale();
    }
}
