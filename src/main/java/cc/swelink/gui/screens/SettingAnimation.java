package cc.swelink.gui.screens;

import cc.swelink.renderer.SpringAnimation;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.ColorSetting;
import cc.swelink.settings.Setting;
import cc.swelink.settings.SliderSetting;

import java.util.HashMap;
import java.util.Map;

final class SettingAnimation {
    float valueLeft;
    final Map<String, SpringAnimation> modeHover = new HashMap<>();
    final SpringAnimation hoverMotion = new SpringAnimation();
    final SpringAnimation pulseMotion = new SpringAnimation();
    final SpringAnimation hueMotion;
    final SpringAnimation saturationMotion;
    final SpringAnimation brightnessMotion;
    final SpringAnimation dropdownMotion = new SpringAnimation();
    final SpringAnimation visibilityMotion;
    final SpringAnimation enabledMotion;
    final SpringAnimation fractionMotion;
    final SpringAnimation chipMotion = new SpringAnimation(-1);
    float sliderTarget;
    float dropdown;
    float visibility;
    float enabled;
    float fraction;
    float hover;
    float pulse;
    float hue;
    float saturation;
    float brightness;

    SettingAnimation(Setting<?> setting) {
        visibility = setting.isVisible() ? 1 : 0;
        enabled = setting instanceof BooleanSetting toggle && toggle.get() ? 1 : 0;
        fraction = setting instanceof SliderSetting slider ? slider.fraction() : 0;
        visibilityMotion = new SpringAnimation(visibility);
        enabledMotion = new SpringAnimation(enabled);
        fractionMotion = new SpringAnimation(fraction);
        sliderTarget = fraction;
        if (setting instanceof ColorSetting color) {
            hue = color.hue();
            saturation = color.saturation();
            brightness = color.brightness();
        }
        hueMotion = new SpringAnimation(hue);
        saturationMotion = new SpringAnimation(saturation);
        brightnessMotion = new SpringAnimation(brightness);
    }
}
