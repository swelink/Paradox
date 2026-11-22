package cc.swelink.utils.player;

public final class HorizontalSpeed {
    private long lastTick = Long.MIN_VALUE;
    private double lastX;
    private double lastZ;
    private float speed;
    private float previousSpeed;

    public void sample(double x, double z, long tick, float tickRate) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            reset();
            return;
        }
        if (tick == lastTick) return;
        double distance = Math.hypot(x - lastX, z - lastZ);
        if (lastTick == Long.MIN_VALUE || tick < lastTick || distance > 64) {
            speed = previousSpeed = 0;
        } else {
            float rate = Float.isFinite(tickRate) && tickRate > 0 ? tickRate : 20;
            previousSpeed = speed;
            speed = (float) (distance * rate / (tick - lastTick));
        }
        lastX = x;
        lastZ = z;
        lastTick = tick;
    }

    public float speed() {
        return speed;
    }

    public float previousSpeed() {
        return previousSpeed;
    }

    public void reset() {
        speed = previousSpeed = 0;
        lastTick = Long.MIN_VALUE;
    }
}
