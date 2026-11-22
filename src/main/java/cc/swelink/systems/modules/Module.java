package cc.swelink.systems.modules;

import cc.swelink.Paradox;
import cc.swelink.settings.BindModeSetting;
import cc.swelink.settings.BindSetting;
import cc.swelink.settings.Setting;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private List<Setting<?>> settingsSnapshot = List.of();
    private final BindModeSetting bindMode = new BindModeSetting();
    private final BindSetting bind = new BindSetting();
    private boolean enabled;

    protected Module(String name, String description) {
        this(name, description, Category.RENDER);
    }

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        updateSettings();
    }

    public Category category() {
        return category;
    }

    protected <T extends Setting<?>> T addSetting(T setting) {
        settings.add(setting);
        updateSettings();
        return setting;
    }

    private void updateSettings() {
        List<Setting<?>> all = new ArrayList<>(settings);
        List<Setting<?>> raycast = new ArrayList<>();
        all.removeIf(setting -> setting.name().equals("Raycast") && raycast.add(setting));
        all.addAll(raycast);
        all.add(bindMode);
        all.add(bind);
        settingsSnapshot = List.copyOf(all);
    }

    public BindSetting bind() {
        return bind;
    }

    public BindModeSetting bindMode() {
        return bindMode;
    }

    public String name() {
        return name;
    }

    public long delayRemaining() {
        return -1;
    }

    public long delayLimit() {
        return -1;
    }

    public String description() {
        return description;
    }

    public List<Setting<?>> settings() {
        return settingsSnapshot;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
            if (this.enabled && !alwaysListening()) Paradox.EVENTS.subscribe(this);
        } else {
            if (!alwaysListening()) Paradox.EVENTS.unsubscribe(this);
            onDisable();
        }
        if (this.enabled == enabled && announcesToggle()) {
            Paradox.MODULES.notifyToggle(this, enabled);
        }
    }

    protected boolean announcesToggle() {
        return true;
    }

    protected void onEnable() {
    }

    protected boolean alwaysListening() {
        return false;
    }

    protected void onDisable() {
    }

    public void tick(Minecraft client) {
    }

    public void tickInput(Minecraft client) {
    }
}
