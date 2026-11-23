package cc.swelink.systems.modules.client;

import cc.swelink.gui.screens.ClickGuiScreen;
import cc.swelink.gui.screens.HudEditorScreen;
import cc.swelink.renderer.Theme;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.ModuleManager;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.Arrays;


public class ClickGuiModule extends Module {
    private final ModeSetting theme = addSetting(new ModeSetting("Theme", Theme.PARADOX.title,
            Arrays.stream(Theme.values()).map(value -> value.title).toArray(String[]::new)));
    private final BooleanSetting shadows = addSetting(new BooleanSetting("Shadows", true));
    private final BooleanSetting islandShadows = addSetting(new BooleanSetting("Island shadows", true));
    private final ClickGuiScreen screen;
    private final ModuleManager manager;
    private boolean returning;

    public ClickGuiModule(ModuleManager manager) {
        super("ClickGUI", "Click the gui", Category.CLIENT);
        this.manager = manager;
        screen = new ClickGuiScreen(manager, this, this::accentColor);
        bind().bind(InputConstants.KEY_RSHIFT);
    }

    public void returnFromHudEditor() {
        returning = true;
        try {
            if (isEnabled()) onEnable();
            else setEnabled(true);
        } finally {
            returning = false;
        }
    }

    public void openHudEditor() {
        var gui = Minecraft.getInstance().gui;
        var current = gui.screen();
        if (current == screen) {
            screen.openHudEditor();
        } else if (!(current instanceof HudEditorScreen)) {
            gui.setScreen(new HudEditorScreen(manager, this::accentColor,
                    current != null && restorable(current) ? current : null, false));
        }
    }

    @Override
    protected void onEnable() {
        var gui = Minecraft.getInstance().gui;
        var current = gui.screen();
        if (current instanceof HudEditorScreen editor && !returning) {
            setEnabled(false);
            editor.exit();
            return;
        }
        if (current == screen && screen.reopen()) return;
        if (current instanceof HudEditorScreen editor) {
            screen.returnTo(editor.returnTo());
            screen.cleanViewBackground(editor.returnTo());
            screen.carryWindow();
        } else if (current != screen) {
            screen.returnTo(restorable(current) ? current : null);
            screen.cleanViewBackground(current);
        }
        gui.setScreen(screen);
    }

    public boolean canOpenOver(Screen current) {
        return !(current instanceof ChatScreen || current instanceof LevelLoadingScreen || current instanceof ConnectScreen
                || current instanceof ProgressScreen || current instanceof GenericMessageScreen
                || current instanceof GenericWaitingScreen || current.getFocused() instanceof EditBox);
    }

    private static boolean restorable(Screen current) {
        return !(current instanceof AbstractContainerScreen<?>) || current instanceof InventoryScreen
                || current instanceof CreativeModeInventoryScreen;
    }

    @Override
    protected void onDisable() {
        if (screen.isOpen() && Minecraft.getInstance().gui.screen() == screen) {
            screen.onClose();
        }
    }



    public boolean shadows() {
        return shadows.get();
    }

    public boolean islandShadows() {
        return islandShadows.get();
    }

    public Theme theme() {
        return Theme.named(theme.get());
    }

    public int accentColor() {
        return theme().primary;
    }

    public boolean isBinding() {
        return screen.isBinding();
    }

    public void tickAnimations() {
        screen.tickClosedAnimations();
    }
}
