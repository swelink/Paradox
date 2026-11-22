package cc.swelink.gui.screens;

import cc.swelink.gui.previews.EspPlayerPreview;
import cc.swelink.settings.PlayerPreviewSetting;
import cc.swelink.settings.Setting;
import cc.swelink.settings.SliderSetting;
import cc.swelink.systems.modules.Module;

record Row(Module module, Setting<?> setting, float amount, float groupOffset, float groupHeight,
           boolean spacer, String modeChoice, boolean colorPicker, float pad) {
    Row(Module module, Setting<?> setting, float amount) {
        this(module, setting, amount, 0, 0, false, null, false);
    }

    Row(Module module, Setting<?> setting, float amount, float groupOffset, float groupHeight,
        boolean spacer, String modeChoice, boolean colorPicker) {
        this(module, setting, amount, groupOffset, groupHeight, spacer, modeChoice, colorPicker, 0);
    }

    static Row spacing(Module module, float amount) {
        return new Row(module, null, amount, 0, 0, true, null, false);
    }

    float height() {
        if (spacer) {
            return ClickGuiStyle.SETTINGS_SPACING * amount;
        }
        return ((colorPicker ? ClickGuiStyle.PICKER_HEIGHT : setting == null ? ClickGuiStyle.ROW_HEIGHT
                : settingHeight(setting)) + pad * 2) * amount;
    }

    static float settingHeight(Setting<?> setting) {
        if (setting instanceof PlayerPreviewSetting) {
            return EspPlayerPreview.HEIGHT;
        }
        return setting instanceof SliderSetting ? ClickGuiStyle.SLIDER_HEIGHT : ClickGuiStyle.SETTING_ROW_HEIGHT;
    }
}
