package cc.swelink.systems.modules.render;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;

public final class PostProcessingModule extends Module {
    public PostProcessingModule() {
        super("PostProcessing", "Blur and fade effects on HUD and GUI", Category.RENDER);
        setEnabled(true);
    }

    public static boolean active() {
        var module = Paradox.MODULES.get(PostProcessingModule.class);
        return module == null || module.isEnabled();
    }
}
