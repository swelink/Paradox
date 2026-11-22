package cc.swelink.systems.modules.world;

import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;

import java.util.Locale;

public final class TimerModule extends Module {
    private final IntSetting multiplier = addSetting(new IntSetting("Multiplier", 10, 1, 100) {
        @Override
        protected String formatValue(int value) {
            return String.format(Locale.ROOT, "%.1f", value / 10f);
        }
    });

    public TimerModule() {
        super("Timer", "Changes game speed", Category.PLAYER);
    }

    public float multiplier() {
        return isEnabled() ? multiplier.get() / 10f : 1f;
    }
}
