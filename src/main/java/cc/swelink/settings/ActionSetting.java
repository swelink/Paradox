package cc.swelink.settings;

public final class ActionSetting extends Setting<Void> {
    private final Runnable action;

    public ActionSetting(String name, Runnable action) {
        super(name, null);
        this.action = action;
    }

    @Override
    public void cycle() {
        action.run();
    }

    @Override
    public String displayValue() {
        return "";
    }
}
