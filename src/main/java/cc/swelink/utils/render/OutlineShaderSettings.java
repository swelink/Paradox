package cc.swelink.utils.render;

import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.settings.Setting;
import net.minecraft.client.renderer.PostChain;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class OutlineShaderSettings {
    private final IntSetting width = new IntSetting("Outline width", 2, 1, 5);
    private final ModeSetting shape = new ModeSetting("Shape", "Both", "Outline", "Fill", "Both");
    private final PercentSetting fill = new PercentSetting("Fill opacity", 25);
    private final IntSetting glow = new IntSetting("Glow", 3, 1, 10);

    public OutlineShaderSettings(Consumer<Setting<?>> register, BooleanSupplier visible) {
        width.visibleWhen(() -> visible.getAsBoolean() && !shape.get().equals("Fill"));
        shape.visibleWhen(visible);
        fill.visibleWhen(() -> visible.getAsBoolean() && !shape.get().equals("Outline"));
        glow.visibleWhen(() -> visible.getAsBoolean() && !shape.get().equals("Fill"));
        register.accept(width);
        register.accept(shape);
        register.accept(fill);
        register.accept(glow);
    }

    public int width() { return width.get(); }
    public float fill() { return fill.get() / 100f; }
    public int glow() { return glow.get(); }
    public int shape() { return shape.get().equals("Outline") ? 0 : shape.get().equals("Fill") ? 1 : 2; }

    public void configure(PostChain chain) {
        ShaderKernels.configure(chain, width(), glow());
        PostEffectUtils.writeUniform(chain, "EspConfig", width(), fill(), glow(), shape());
    }
}
