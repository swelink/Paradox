package cc.swelink.settings;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public void setValue(boolean value) {
        set(value);
    }

    @Override
    public void cycle() {
        set(!get());
    }

    @Override
    public String displayValue() {
        return get() ? "On" : "Off";
    }
}
