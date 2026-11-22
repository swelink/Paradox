package cc.swelink.gui.screens;

import cc.swelink.systems.modules.ModuleManager;

import java.util.function.IntSupplier;

final class HudModulesMenu extends ClickGuiScreen {
    HudModulesMenu(ModuleManager manager, IntSupplier accent) {
        super(manager, null, accent, true);
    }

    boolean contains(double mouseX, double mouseY) {
        return windowContains(mouseX, mouseY);
    }
}
