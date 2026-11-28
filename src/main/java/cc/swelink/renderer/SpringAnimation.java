package cc.swelink.renderer;

public final class SpringAnimation {
    private static final double LANDING = 0.06;
    private float value;
    private float velocity;
    private float lastTarget = Float.NaN;
    private float span;

    public SpringAnimation() { }

    public SpringAnimation(float value) {
        reset(value);
    }

    public float update(float target, float speed, float delta) {
        return update(target, speed, 1, delta);
    }

    public float update(float target, float speed, float damping, float delta) {
        if (!(delta > 0)) return value;
        if (Float.compare(target, lastTarget) != 0) {
            lastTarget = target;
            span = Math.abs(value - target);
        }
        if (value == target && velocity == 0) return value;
        boolean critical = damping >= 0.999f;
        double direction = critical ? Math.signum(target - value) : 0;
        double aim = target + direction * span * LANDING;
        double offset = value - aim;
        double decay = Math.exp(-damping * speed * delta);
        double nextOffset;
        double nextVelocity;
        if (critical) {
            double coefficient = velocity + speed * offset;
            nextOffset = (offset + coefficient * delta) * decay;
            nextVelocity = (velocity - speed * coefficient * delta) * decay;
        } else {
            double frequency = speed * Math.sqrt(1 - damping * damping);
            double coefficient = (velocity + damping * speed * offset) / frequency;
            double sine = Math.sin(frequency * delta);
            double cosine = Math.cos(frequency * delta);
            nextOffset = (offset * cosine + coefficient * sine) * decay;
            nextVelocity = (-offset * frequency * sine + coefficient * frequency * cosine
                    - damping * speed * (offset * cosine + coefficient * sine)) * decay;
        }
        double next = aim + nextOffset;
        if (direction != 0 && (target - next) * direction <= 0) {
            reset(target);
            return value;
        }
        value = (float) next;
        velocity = (float) nextVelocity;
        float rest = Math.max(0.0005f, span * 0.002f);
        if (Math.abs(value - target) < rest && Math.abs(velocity) < rest * 10) reset(target);
        return value;
    }

    public void reset(float value) {
        this.value = value;
        velocity = 0;
        lastTarget = Float.NaN;
    }

    public float value() {
        return value;
    }

    public float velocity() {
        return velocity;
    }
}
