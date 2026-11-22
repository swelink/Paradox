package cc.swelink.settings;

public final class RaycastSetting extends ModeSetting {
    public RaycastSetting() {
        super("Raycast", "Normal", "Normal", "Target");
    }

    public boolean target() {
        return get().equals("Target");
    }
}
