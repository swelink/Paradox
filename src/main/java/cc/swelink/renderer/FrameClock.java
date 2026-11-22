package cc.swelink.renderer;

public final class FrameClock {
    private static final float FALLBACK_DELTA = 1f / 60;
    private long lastFrameTime;

    public float nextDelta() {
        long now = System.nanoTime();
        float delta = lastFrameTime == 0 ? FALLBACK_DELTA : Math.min(.1f, (now - lastFrameTime) * 1e-9f);
        lastFrameTime = now;
        return delta;
    }

    public void reset() {
        lastFrameTime = 0;
    }

    public static float approach(float current, float target, float speed, float deltaTime) {
        return target + (current - target) * (float) Math.exp(-speed * deltaTime);
    }

    public static float smoothStep(float value) {
        return value * value * (3 - 2 * value);
    }


}
