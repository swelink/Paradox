package cc.swelink.settings;

import cc.swelink.systems.modules.render.esp.EspModule;

public final class PlayerPreviewSetting extends Setting<Void> {
    private final EspModule module;

    public PlayerPreviewSetting(EspModule module) {
        super("Player preview", null);
        this.module = module;
    }

    public EspModule module() {
        return module;
    }

    @Override
    public void cycle() {
    }

    @Override
    public String displayValue() {
        return "";
    }
}
