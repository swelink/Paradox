package cc.swelink.settings;

import java.awt.Color;
import java.util.Locale;

public class ColorSetting extends Setting<Integer> {
    private float hue;
    private float saturation;
    private float brightness;

    public ColorSetting(String name, int color) {
        super(name, color | 0xFF000000);
        float[] hsv = Color.RGBtoHSB((color >> 16) & 255, (color >> 8) & 255, color & 255, null);
        hue = hsv[0];
        saturation = hsv[1];
        brightness = hsv[2];
    }

    public float hue() {
        return hue;
    }

    public float saturation() {
        return saturation;
    }

    public float brightness() {
        return brightness;
    }

    public void select(float hue, float saturation, float brightness) {
        this.hue = Math.clamp(hue, 0, 1);
        this.saturation = Math.clamp(saturation, 0, 1);
        this.brightness = Math.clamp(brightness, 0, 1);
        set(rgb(this.hue, this.saturation, this.brightness));
    }

    public static int rgb(float hue, float saturation, float brightness) {
        return Color.HSBtoRGB(hue, saturation, brightness);
    }

    @Override
    public void cycle() {
    }

    @Override
    public String displayValue() {
        return String.format(Locale.ROOT, "#%06X", get() & 0xFFFFFF);
    }
}
