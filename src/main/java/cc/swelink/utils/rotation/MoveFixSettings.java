package cc.swelink.utils.rotation;

import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.Setting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class MoveFixSettings {
    private final BooleanSetting enabled = new BooleanSetting("Movefix", true);
    private final ModeSetting mode = new ModeSetting("Movefix mode", "Silent", "Strict", "Silent");

    public MoveFixSettings(Consumer<Setting<?>> register, BooleanSupplier visible) {
        enabled.visibleWhen(visible);
        mode.visibleWhen(() -> visible.getAsBoolean() && enabled.get());
        register.accept(enabled);
        register.accept(mode);
    }

    public MoveFix get() {
        return !enabled.get() ? MoveFix.OFF
                : mode.get().equals("Strict") ? MoveFix.STRICT : MoveFix.SILENT;
    }
}
