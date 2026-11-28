package cc.swelink.settings;

public class StringSetting extends Setting<String> {
    private final int maxLength;

    public StringSetting(String name, String defaultValue, int maxLength) {
        super(name, defaultValue);
        this.maxLength = maxLength;
    }

    public void setValue(String value) {
        set(value.length() > maxLength ? value.substring(0, maxLength) : value);
    }

    public int maxLength() {
        return maxLength;
    }

    @Override
    public void cycle() {
    }

    @Override
    public String displayValue() {
        return get();
    }
}
