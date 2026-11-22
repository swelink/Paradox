package cc.swelink.settings;

public final class MillisecondsSetting extends IntSetting {
    public MillisecondsSetting(String name, int value, int min, int max) {
        super(name, value, min, max);
    }

    @Override
    protected String formatValue(int value) {
        return value + " ms";
    }
}
