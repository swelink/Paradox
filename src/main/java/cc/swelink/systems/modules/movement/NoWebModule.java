package cc.swelink.systems.modules.movement;

import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;

public final class NoWebModule extends Module {
    public NoWebModule() {
        super("NoWeb", "No slowdown in cobwebs", Category.MOVEMENT);
    }

    public boolean stuck() {
        return isEnabled();
    }
}
