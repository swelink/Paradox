package cc.swelink.utils.render;

import cc.swelink.renderer.ThemeColors;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.settings.Setting;
import net.minecraft.client.renderer.PostChain;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class OutlineShaderSettings {
    private static final long WAVE_START = System.nanoTime();
    private final IntSetting width = new IntSetting("Outline width", 2, 1, 5);
    private final ModeSetting shape = new ModeSetting("Shape", "Both", "Outline", "Fill", "Both");
    private final PercentSetting fill = new PercentSetting("Fill opacity", 25);
    private final ModeSetting wave = new ModeSetting("Animation", "Simple", "Off", "Simple", "Marbled", "Matrix", "Glitter");
    private final IntSetting glow = new IntSetting("Glow", 3, 1, 10);

    public OutlineShaderSettings(Consumer<Setting<?>> register, BooleanSupplier visible) {
        width.visibleWhen(() -> visible.getAsBoolean() && !shape.get().equals("Fill"));
        shape.visibleWhen(visible);
        fill.visibleWhen(() -> visible.getAsBoolean() && !shape.get().equals("Outline"));
        wave.visibleWhen(() -> visible.getAsBoolean() && !shape.get().equals("Outline"));
        glow.visibleWhen(() -> visible.getAsBoolean() && !shape.get().equals("Fill"));
        register.accept(width);
        register.accept(shape);
        register.accept(fill);
        register.accept(glow);
        register.accept(wave);
    }

    public int waveMode() {
        return switch (wave.get()) {
            case "Off" -> 0;
            case "Marbled" -> 2;
            case "Matrix" -> 3;
            case "Glitter" -> 4;
            default -> 1;
        };
    }

    public int width() { return width.get(); }
    public float fill() { return fill.get() / 100f; }
    public int glow() { return glow.get(); }
    public int shape() { return shape.get().equals("Outline") ? 0 : shape.get().equals("Fill") ? 1 : 2; }

    public void configure(PostChain chain) {
        ShaderKernels.configure(chain, width(), glow());
        PostEffectUtils.writeUniform(chain, "EspConfig", width(), fill(), glow(), shape());
        configureWave(chain, waveMode());
    }
    public static void configureWave(PostChain chain, int mode) {
        float time = (float) ((System.nanoTime() - WAVE_START) * 1e-9);
        int primary = ThemeColors.primary();
        int secondary = ThemeColors.secondary();
        PostEffectUtils.writeUniform(chain, "FillWave", mode, time, 0, 0,
                ((primary >>> 16) & 255) / 255f, ((primary >>> 8) & 255) / 255f, (primary & 255) / 255f, 1,
                ((secondary >>> 16) & 255) / 255f, ((secondary >>> 8) & 255) / 255f, (secondary & 255) / 255f, 1);
    }
}
