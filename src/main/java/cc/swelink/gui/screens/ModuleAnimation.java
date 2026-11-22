package cc.swelink.gui.screens;

import cc.swelink.renderer.SpringAnimation;

final class ModuleAnimation {
    final SpringAnimation hoverMotion = new SpringAnimation();
    final SpringAnimation enabledMotion;
    final SpringAnimation openMotion = new SpringAnimation();
    float open;
    float enabled;
    float hover;

    ModuleAnimation(boolean enabled) {
        this.enabled = enabled ? 1 : 0;
        enabledMotion = new SpringAnimation(this.enabled);
    }
}
