package cc.swelink.gui.screens;

import cc.swelink.settings.Setting;
import cc.swelink.settings.SliderSetting;
import cc.swelink.systems.modules.Module;

record Row(Module module, Setting<?> setting, float amount, float groupOffset, float groupHeight,
           boolean spacer, String modeChoice, boolean colorPicker, float baseHeight) {
    Row(Module module, Setting<?> setting, float amount, float groupOffset, float groupHeight,
        boolean spacer, String modeChoice, boolean colorPicker) {
        this(module, setting, amount, groupOffset, groupHeight, spacer, modeChoice, colorPicker, Float.NaN);
    }

    Row(Module module, Setting<?> setting, float amount) {
        this(module, setting, amount, 0, 0, false, null, false);
    }

    Row sized(float height) {
        return new Row(module, setting, amount, groupOffset, groupHeight, spacer, modeChoice, colorPicker, height);
    }

    Row copy(float amount, float groupOffset, float groupHeight) {
        return new Row(module, setting, amount, groupOffset, groupHeight, spacer, modeChoice, colorPicker, baseHeight);
    }

    static Row module(Module module, float height) {
        return new Row(module, null, 1, 0, 0, false, null, false, height);
    }

    static Row spacing(Module module, float amount) {
        return new Row(module, null, amount, 0, 0, true, null, false);
    }

    float height() {
        if (spacer) {
            return ClickGuiStyle.SETTINGS_SPACING * amount;
        }
        float base = !Float.isNaN(baseHeight) ? baseHeight : colorPicker ? ClickGuiStyle.PICKER_HEIGHT : settingHeight(setting);
        return base * amount;
    }

    static float settingHeight(Setting<?> setting) {
        return setting instanceof SliderSetting ? ClickGuiStyle.SLIDER_HEIGHT : ClickGuiStyle.SETTING_ROW_HEIGHT;
    }
}
