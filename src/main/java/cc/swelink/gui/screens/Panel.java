package cc.swelink.gui.screens;

import cc.swelink.renderer.SpringAnimation;
import cc.swelink.systems.modules.Category;

final class Panel {
    final Category category;
    float x;
    float y;
    float scroll;
    float targetScroll;
    float targetX;
    float targetY;
    float amount = 1;
    final SpringAnimation xMotion;
    final SpringAnimation yMotion;
    final SpringAnimation scrollMotion = new SpringAnimation();
    final SpringAnimation amountMotion = new SpringAnimation(1);
    boolean open = true;

    Panel(Category category, float x, float y) {
        this.category = category;
        this.x = this.targetX = x;
        this.y = this.targetY = y;
        xMotion = new SpringAnimation(x);
        yMotion = new SpringAnimation(y);
    }
}
