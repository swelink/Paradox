package cc.swelink.settings;

import java.util.Locale;

public final class SecondsSetting extends IntSetting {
    public SecondsSetting(String name, int defaultTenths, int maxTenths) {
        this(name, defaultTenths, 0, maxTenths);
    }

    public SecondsSetting(String name, int defaultTenths, int minTenths, int maxTenths) {
        super(name, defaultTenths, minTenths, maxTenths);
    }

    public double seconds() {
        return get() / 10.0;
    }

    @Override
    protected String formatValue(int value) {
        return String.format(Locale.ROOT, "%.1fs", value / 10.0);
    }
}
