package cc.swelink.gui.screens;

import cc.swelink.renderer.UiTokens;
import cc.swelink.renderer.ThemeColors;

public final class ClickGuiStyle {
    static final float CARD_RADIUS = UiTokens.RADIUS_PANEL;
    static final float MODULE_FONT_SIZE = UiTokens.TEXT_EMPHASIS;
    static final float SETTING_FONT_SIZE = UiTokens.TEXT_CAPTION;
    static final float FONT_SIZE = UiTokens.TEXT_BODY;
    static final float SETTINGS_SPACING = 4;
    static final float SETTING_ROW_HEIGHT = 24;
    static final float SLIDER_HEIGHT = 36;
    static final float PICKER_HEIGHT = 104;
    static final int PANEL_HEADER = 0xFF151415;
    static final int PANEL_BODY = 0xFF1F1F1F;
    static final int PANEL_INSET = 0xFF171717;
    static final int CONTROL = 0xFF2A2A2A;
    static final int CONTROL_DARK = 0xFF232323;
    static final int TRACK = 0xFF3A3A3A;
    static final int TITLE = 0xFFF8F8F6;
    static final int MODULE_TEXT = 0xFFB4B4B4;
    static final int MODULE_HOVER = 0xFFE2E2E2;
    static final int LABEL = UiTokens.TEXT_TERTIARY;
    static final int KEY_TEXT = UiTokens.TEXT_TERTIARY;
    static final int ARROW = 0xFF555555;

    static int header() {
        return ThemeColors.theme().header;
    }

    static int raised() {
        return ThemeColors.theme().raised;
    }

    public static int disabled() {
        return ThemeColors.theme().disabled;
    }

    public static int text() {
        return ThemeColors.theme().text;
    }

    static int muted() {
        return ThemeColors.theme().muted;
    }

    private ClickGuiStyle() {
    }
}
