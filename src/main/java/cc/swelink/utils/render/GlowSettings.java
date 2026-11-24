package cc.swelink.utils.render;

import cc.swelink.settings.IntSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.settings.Setting;
import net.minecraft.client.renderer.PostChain;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class GlowSettings {
    private final IntSetting radius = new IntSetting("Glow radius", 6, 1, 10);
    private final IntSetting intensity = new IntSetting("Glow intensity", 100, 25, 200);
    private final IntSetting outline = new IntSetting("Outline width", 1, 0, 3);
    private final PercentSetting fill;

    public GlowSettings(Consumer<Setting<?>> register, BooleanSupplier visible, int defaultFill) {
        fill = new PercentSetting("Fill opacity", defaultFill);
        for (var setting : new Setting<?>[]{radius, intensity, outline, fill}) {
            setting.visibleWhen(visible);
            register.accept(setting);
        }
    }

    public void configure(PostChain chain, int tint) {
        ShaderKernels.configureBlur(chain, radius.get());
        PostEffectUtils.writeUniform(chain, "GlowConfig", intensity.get() / 100f, fill.get() / 100f, outline.get(), 0);
        PostEffectUtils.writeUniform(chain, "GlowTint", ((tint >>> 16) & 255) / 255f,
                ((tint >>> 8) & 255) / 255f, (tint & 255) / 255f, 1);
    }
}
