package cc.swelink.systems.modules.render.esp.mode;

import cc.swelink.settings.Setting;
import cc.swelink.systems.modules.render.esp.EspModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public abstract class EspMode {
    protected final EspModule module;
    private final String name;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected EspMode(EspModule module, String name) {
        this.module = module;
        this.name = name;
    }

    protected <T extends Setting<?>> T add(T setting) {
        setting.visibleWhen(() -> module.selected(this));
        settings.add(setting);
        return setting;
    }

    public final String name() {
        return name;
    }

    public final List<Setting<?>> settings() {
        return List.copyOf(settings);
    }

    public void enable() {
    }

    public void disable() {
    }

    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
    }
}
