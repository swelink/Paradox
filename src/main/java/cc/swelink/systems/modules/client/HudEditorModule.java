package cc.swelink.systems.modules.client;

import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.ModuleManager;

public final class HudEditorModule extends Module {
    private final ModuleManager manager;

    public HudEditorModule(ModuleManager manager) {
        super("HudEditor", "You can read btw", Category.CLIENT);
        this.manager = manager;
    }

    @Override
    protected boolean announcesToggle() {
        return false;
    }

    @Override
    protected void onEnable() {
        setEnabled(false);
        manager.get(ClickGuiModule.class).openHudEditor();
    }
}
