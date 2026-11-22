package cc.swelink.settings;

public class StepSetting extends IntSetting {
    private final int[] steps;

    public StepSetting(String name, int defaultValue, int... steps) {
        super(name, defaultValue, steps[0], steps[steps.length - 1]);
        this.steps = steps.clone();
        setValue(defaultValue);
    }

    @Override
    public void cycle() {
        int index = index(get());
        set(steps[index == steps.length - 1 ? 0 : index + 1]);
    }

    @Override
    public void setValue(int value) {
        set(steps[index(value)]);
    }

    @Override
    public String displayValue(float fraction) {
        if (steps[index(get())] != get()) return formatValue(get());
        return formatValue(steps[Math.round(Math.clamp(fraction, 0, 1) * (steps.length - 1))]);
    }

    @Override
    public float fraction() {
        return steps.length == 1 ? 0 : (float) index(get()) / (steps.length - 1);
    }

    @Override
    public void setFraction(float fraction) {
        set(steps[Math.round(Math.clamp(fraction, 0, 1) * (steps.length - 1))]);
    }

    private int index(int value) {
        int closest = 0;
        for (int i = 1; i < steps.length; i++) {
            if (Math.abs(steps[i] - value) < Math.abs(steps[closest] - value)) closest = i;
        }
        return closest;
    }
}
