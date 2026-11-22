package cc.swelink.systems.modules.client;

import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.ModuleManager;

public final class ConfigModule extends Module {
    private final ModuleManager manager;

    public ConfigModule(ModuleManager manager) {
        super("Config", "as i said you can read", Category.CLIENT);
        this.manager = manager;
    }

    @Override
    protected boolean announcesToggle() {
        return false;
    }

    @Override
    protected void onEnable() {
        setEnabled(false);
        manager.get(ClickGuiModule.class).openConfig();
    }
}
