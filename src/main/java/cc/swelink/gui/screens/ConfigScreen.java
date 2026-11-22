package cc.swelink.gui.screens;

import cc.swelink.systems.modules.ModuleManager;
import cc.swelink.systems.modules.client.ClickGuiModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.IntSupplier;

public final class ConfigScreen extends ClickGuiScreen {
    private final ModuleManager manager;
    private Screen back;
    private boolean switched;

    public ConfigScreen(ModuleManager manager, IntSupplier accent) {
        super(manager, null, accent, false, true);
        this.manager = manager;
    }

    public void show(Screen back, boolean switched) {
        this.back = back;
        this.switched = switched;
        if (switched) carryWindow();
        returnTo(back);
        cleanViewBackground(back);
        Minecraft.getInstance().gui.setScreen(this);
    }

    public Screen back() {
        return back;
    }

    @Override
    public void onClose() {
        if (switched) {
            switched = false;
            manager.get(ClickGuiModule.class).returnFromConfig();
            return;
        }
        super.onClose();
    }

    public void exit() {
        Minecraft.getInstance().gui.setScreen(back);
    }
}
