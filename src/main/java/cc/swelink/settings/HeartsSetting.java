package cc.swelink.settings;

import java.util.Locale;

public final class HeartsSetting extends IntSetting {
    public HeartsSetting(String name, int defaultHealth, int minHealth, int maxHealth) {
        super(name, defaultHealth, minHealth, maxHealth);
    }

    @Override
    protected String formatValue(int value) {
        return String.format(Locale.ROOT, value % 2 == 0 ? "%.0f hearts" : "%.1f hearts", value / 2.0);
    }
}
