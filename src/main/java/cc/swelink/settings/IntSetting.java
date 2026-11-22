package cc.swelink.settings;

import java.util.regex.Pattern;

public class IntSetting extends Setting<Integer> implements SliderSetting {
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(?:\\.\\d+)?");

    private final int min;
    private final int max;
    private boolean bounded;

    public IntSetting(String name, int defaultValue, int min, int max) {
        super(name, Math.clamp(defaultValue, Math.min(min, max), Math.max(min, max)));
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
    }

    @Override
    public void cycle() {
        set(get() >= max ? min : get() + 1);
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public void setValue(int value) {
        set(Math.max(min, Math.min(max, value)));
    }

    @Override
    public String displayValue() {
        return formatValue(get());
    }

    public String displayValue(float fraction) {
        if (get() < min || get() > max) return formatValue(get());
        return formatValue(Math.round(min + Math.clamp(fraction, 0, 1) * (max - min)));
    }

    public IntSetting bounded() {
        bounded = true;
        return this;
    }

    public void restore(int value) {
        set(Math.clamp(value, min, bounded ? max : Integer.MAX_VALUE));
    }

    public String typedValue() {
        var number = NUMBER.matcher(displayValue());
        return number.find() ? number.group() : "";
    }

    public String typedSuffix() {
        var number = NUMBER.matcher(displayValue());
        return number.find() ? displayValue().substring(number.end()) : "";
    }

    public void setTyped(double typed) {
        double low = shown(min);
        double high = shown(max);
        double scale = max == min || high == low ? 1 : (high - low) / (max - min);
        long value = Math.round(min + (typed - low) / scale);
        restore(Math.clamp(value, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    private double shown(int value) {
        var number = NUMBER.matcher(formatValue(value));
        return number.find() ? Double.parseDouble(number.group()) : value;
    }

    protected String formatValue(int value) {
        return Integer.toString(value);
    }

    @Override
    public float fraction() {
        if (min == max) {
            return 0;
        }
        return Math.clamp((float) (get() - min) / (max - min), 0, 1);
    }

    @Override
    public void setFraction(float fraction) {
        setValue(Math.round(min + Math.clamp(fraction, 0, 1) * (max - min)));
    }
}
