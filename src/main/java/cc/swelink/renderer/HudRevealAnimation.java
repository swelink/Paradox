package cc.swelink.renderer;

public final class HudRevealAnimation {
    private float space;
    private float slide;

    public void update(boolean enabled, float delta) {
        if (enabled || slide == 0) space = advance(space, enabled ? 1 : 0, delta / 0.18f);
        if (!enabled || space == 1 || slide > 0) slide = advance(slide, enabled ? 1 : 0, delta / 0.22f);
    }

    public float space() {
        return FrameClock.smoothStep(space);
    }

    public float slide() {
        return FrameClock.smoothStep(slide);
    }

    public void reset(boolean enabled) {
        space = slide = enabled ? 1 : 0;
    }

    private static float advance(float current, float target, float step) {
        return target > current ? Math.min(target, current + step) : Math.max(target, current - step);
    }
}
