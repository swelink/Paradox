package cc.swelink.renderer;

public final class SpringAnimation {
    private float value;
    private float velocity;

    public SpringAnimation() { }

    public SpringAnimation(float value) {
        reset(value);
    }

    public float update(float target, float speed, float delta) {
        return update(target, speed, 1, delta);
    }

    public float update(float target, float speed, float damping, float delta) {
        if (!(delta > 0)) return value;
        double offset = value - target;
        double decay = Math.exp(-damping * speed * delta);
        double nextOffset;
        double nextVelocity;
        if (damping >= 0.999f) {
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
        value = target + (float) nextOffset;
        velocity = (float) nextVelocity;
        if (Math.abs(value - target) < 0.0005f && Math.abs(velocity) < 0.005f) reset(target);
        return value;
    }

    public void reset(float value) {
        this.value = value;
        velocity = 0;
    }

    public float value() {
        return value;
    }

    public float velocity() {
        return velocity;
    }
}
