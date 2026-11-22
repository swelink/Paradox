package cc.swelink.systems.modules.render;

import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;

import java.util.Locale;

public final class TimeModule extends Module {
    private static final int DAY_TICKS = 24000;
    private static final int DAY_MINUTES = 1440;
    private static final int SUNRISE_MINUTES = 360;
    private static final float SWEEP_SPEED = 5;
    private final IntSetting time = addSetting(new IntSetting("Time", 720, 0, DAY_MINUTES - 5) {
        @Override
        protected String formatValue(int value) {
            return String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60);
        }
    });
    private float shown = Float.NaN;
    private float realDayTime;
    private long lastFrame;

    public TimeModule() {
        super("Time", "Changes the time of day", Category.RENDER);
    }

    public boolean active() {
        return isEnabled() || !Float.isNaN(shown);
    }

    public float dayTime(long totalTicks, float partialTick) {
        realDayTime = Math.floorMod(totalTicks, DAY_TICKS) + partialTick;
        long now = System.nanoTime();
        float delta = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) * 1.0e-9f);
        lastFrame = now;
        float target = isEnabled() ? targetTicks() : realDayTime;
        if (Float.isNaN(shown)) shown = realDayTime;
        float difference = wrap(target - shown);
        shown = wrapDay(shown + difference * (1 - (float) Math.exp(-SWEEP_SPEED * delta)));
        if (Math.abs(wrap(target - shown)) < 1) {
            shown = target;
            if (!isEnabled()) {
                shown = Float.NaN;
                return realDayTime;
            }
        }
        return shown;
    }

    private float targetTicks() {
        return Math.floorMod(time.get() - SUNRISE_MINUTES, DAY_MINUTES) * (float) DAY_TICKS / DAY_MINUTES;
    }

    private static float wrap(float ticks) {
        float wrapped = ticks % DAY_TICKS;
        if (wrapped > DAY_TICKS / 2f) wrapped -= DAY_TICKS;
        if (wrapped < -DAY_TICKS / 2f) wrapped += DAY_TICKS;
        return wrapped;
    }

    private static float wrapDay(float ticks) {
        float wrapped = ticks % DAY_TICKS;
        return wrapped < 0 ? wrapped + DAY_TICKS : wrapped;
    }

    @Override
    protected void onEnable() {
        lastFrame = 0;
    }
}
