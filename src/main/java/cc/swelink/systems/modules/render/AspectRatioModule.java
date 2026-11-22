package cc.swelink.systems.modules.render;

import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;

public final class AspectRatioModule extends Module {
    private final ModeSetting ratio = addSetting(new ModeSetting("Ratio", "16:9", "16:10", "4:3", "16:9", "5:4"));

    public AspectRatioModule() {
        super("Aspect Ratio", "Stretches the view", Category.RENDER);
    }

    public float aspectRatio(float original) {
        if (!isEnabled()) return original;
        return switch (ratio.get()) {
            case "16:10" -> 16f / 10;
            case "4:3" -> 4f / 3;
            case "5:4" -> 5f / 4;
            default -> 16f / 9;
        };
    }
}
