package cc.swelink.renderer;

import org.joml.Matrix3x2f;

public final class HudPopAnimation {
    private final SpringAnimation motion = new SpringAnimation();
    private final FrameClock clock = new FrameClock();
    private final boolean smooth;
    private boolean open;
    private float value;

    public HudPopAnimation() {
        this(false);
    }

    public HudPopAnimation(boolean smooth) {
        this.smooth = smooth;
    }

    public void reset() {
        motion.reset(0);
        clock.reset();
        open = true;
        value = 0;
    }

    public void close() {
        open = false;
    }

    public boolean exiting() {
        return !open && value > 0.001f;
    }

    public boolean opening() {
        return open && value < 0.99f;
    }

    public float value() {
        return value;
    }

    public static float pivotX(float x, float width, int edges) {
        if ((edges & BlurRenderer.LEFT) != 0) return x;
        if ((edges & BlurRenderer.RIGHT) != 0) return x + width;
        return x + width / 2;
    }

    public static float pivotY(float y, float height, int edges) {
        if ((edges & BlurRenderer.TOP) != 0) return y;
        if ((edges & BlurRenderer.BOTTOM) != 0) return y + height;
        return y + height / 2;
    }

    public static float scale(float value) {
        return 0.8f + value * 0.2f + 0.04f * (float) Math.sin(Math.PI * value);
    }

    public static void pop(Matrix3x2f pose, float value, float x, float y, float width, float height, int edges) {
        float centerX = pivotX(x, width, edges);
        float centerY = pivotY(y, height, edges);
        pose.translate(centerX, centerY);
        pose.scale(scale(value));
        pose.translate(-centerX, -centerY);
    }

    public void apply(Renderer2D renderer, float x, float y, float width, float height) {
        apply(renderer, x, y, width, height, 0);
    }

    public void apply(Renderer2D renderer, float x, float y, float width, float height, int edges) {
        float delta = clock.nextDelta();
        value = Math.clamp(motion.update(open ? 1 : 0, 20, smooth ? 1 : 0.85f, delta), 0, 1);
        renderer.opacity(value);
        if (smooth) {
            float centerX = pivotX(x, width, edges);
            float centerY = pivotY(y, height, edges);
            var pose = renderer.graphics().pose();
            pose.translate(centerX, centerY);
            pose.scale(0.8f + value * 0.2f);
            pose.translate(-centerX, -centerY);
            return;
        }
        pop(renderer.graphics().pose(), value, x, y, width, height, edges);
    }
}
