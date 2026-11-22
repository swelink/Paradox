package cc.swelink.systems.modules.render;

import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.Locale;

public final class HandViewModule extends Module {
    private static final float PIVOT_Y = -0.52f;
    private static final float PIVOT_Z = -0.72f;
    private static final float DEPTH = 0.45f;
    private static final float LIFT = 0.5f;
    private final IntSetting size = addSetting(new IntSetting("Size", 100, 50, 150) {
        @Override
        protected String formatValue(int value) {
            return value + "%";
        }
    });
    private final IntSetting x = addSetting(offset("X"));
    private final IntSetting y = addSetting(offset("Y"));
    private final IntSetting z = addSetting(offset("Z"));

    public HandViewModule() {
        super("HandView", "Moves and resizes held items", Category.RENDER);
    }

    private static IntSetting offset(String name) {
        return new IntSetting(name, 0, -100, 100) {
            @Override
            protected String formatValue(int value) {
                return String.format(Locale.ROOT, "%.2f", value / 100f);
            }
        };
    }

    public void transform(PoseStack pose, boolean left) {
        if (!isEnabled()) return;
        float scale = size.get() / 100f;
        pose.translate((left ? -x.get() : x.get()) / 100f, y.get() / 100f + Math.max(0, 1 - scale) * LIFT, z.get() / 100f + (scale - 1) * DEPTH);
        if (scale == 1) return;
        pose.translate(0, PIVOT_Y, PIVOT_Z);
        pose.scale(scale, scale, scale);
        pose.translate(0, -PIVOT_Y, -PIVOT_Z);
    }
}
