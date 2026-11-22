package cc.swelink.gui.screens;

import cc.swelink.renderer.ThemeColors;

import net.minecraft.util.ARGB;

public final class ClickGuiStyle {
    static final float ROW_HEIGHT = 36;
    static final float CONTENT_INSET = 18;
    static final float WINDOW_RADIUS = 11.5f;
    static final float CARD_RADIUS = 9.2f;
    static final float MODULE_FONT_SIZE = 14;
    static final float SETTING_FONT_SIZE = 13;
    static final float FONT_SIZE = 14;
    static final float SETTINGS_SPACING = 4;
    static final float SETTING_ROW_HEIGHT = 28;
    static final float SLIDER_HEIGHT = 44;
    static final float PICKER_HEIGHT = 104;
    static final int BORDER = 0x35FFFFFF;
    private static final int GLASS_ALPHA = 0x55000000;

    static int header() {
        return ThemeColors.theme().header;
    }

    static int sidebar() {
        return ThemeColors.theme().sidebar;
    }

    static int island() {
        return ARGB.opaque(ThemeColors.hudPanel());
    }

    static int card() {
        return sidebar();
    }

    static int cardEnabled() {
        return component(ThemeColors.theme().cardEnabled);
    }

    static int settings() {
        return component(ThemeColors.theme().settings);
    }

    static int raised() {
        return component(ThemeColors.theme().raised);
    }

    static int track() {
        return component(ThemeColors.theme().track);
    }

    public static int disabled() {
        return ThemeColors.theme().disabled;
    }

    static int dim() {
        return ThemeColors.theme().dim;
    }

    static int hover() {
        return component(ThemeColors.theme().hover);
    }

    private static int component(int color) {
        return color;
    }

    public static int text() {
        return ThemeColors.theme().text;
    }

    static int muted() {
        return ThemeColors.theme().muted;
    }

    static int glass(int color) {
        return color & 0xFFFFFF | GLASS_ALPHA;
    }

    private ClickGuiStyle() {
    }
}
