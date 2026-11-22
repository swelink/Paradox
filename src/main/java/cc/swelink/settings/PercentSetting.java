package cc.swelink.settings;

public final class PercentSetting extends IntSetting {
    public PercentSetting(String name, int defaultValue) {
        super(name, defaultValue, 0, 100);
    }

    @Override
    protected String formatValue(int value) {
        return value + "%";
    }
}
