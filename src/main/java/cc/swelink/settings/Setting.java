package cc.swelink.settings;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    private final String name;
    private T value;
    private BooleanSupplier visibility = () -> true;

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.value = defaultValue;
    }

    public String name() {
        return name;
    }

    public T get() {
        return value;
    }

    protected void set(T value) {
        this.value = value;
    }

    public Setting<T> visibleWhen(BooleanSupplier visibility) {
        this.visibility = visibility;
        return this;
    }

    public Setting<T> visibleAlsoWhen(BooleanSupplier extra) {
        BooleanSupplier current = visibility;
        visibility = () -> extra.getAsBoolean() && current.getAsBoolean();
        return this;
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    public abstract void cycle();

    public abstract String displayValue();
}
