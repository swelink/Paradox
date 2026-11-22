package cc.swelink.systems.modules.render;

import cc.swelink.renderer.ThemeColors;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.render.PostEffectUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;

public final class AmbienceModule extends Module {
    private static final float FADE_SPEED = 6;
    private static final float MAX_THICKNESS = 0.8f;
    private static final float PASTEL = 0.45f;
    private static final float MAX_BLUR = 14;
    private final IntSetting distance = addSetting(new IntSetting("Distance", 32, 8, 256) {
        @Override
        protected String formatValue(int value) {
            return value + " blocks";
        }
    });
    private final PercentSetting thickness = addSetting(new PercentSetting("Thickness", 75));
    private final PercentSetting opacity = addSetting(new PercentSetting("Opacity", 85));
    private final PercentSetting blur = addSetting(new PercentSetting("Blur", 60));
    private final PercentSetting clouds = addSetting(new PercentSetting("Clouds", 60));
    private final BooleanSetting night = addSetting(new BooleanSetting("Darken At Night", true));
    private final Matrix4f inverse = new Matrix4f();
    private final float[] inverseData = new float[16];
    private final long epoch = System.nanoTime();
    private float strength;
    private long lastFrame;

    public AmbienceModule() {
        super("Ambience", "Accent colored fog", Category.RENDER);
    }

    public boolean rendering() {
        return isEnabled() || strength > 0;
    }

    public boolean configure(PostChain chain, CameraRenderState camera, Matrix4f projection, boolean translucent) {
        long now = System.nanoTime();
        float delta = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) * 1.0e-9f);
        lastFrame = now;
        float target = isEnabled() ? 1 : 0;
        strength += (target - strength) * (1 - (float) Math.exp(-FADE_SPEED * delta));
        if (Math.abs(target - strength) < 0.001f) strength = target;
        if (strength <= 0 || camera.isPanoramicMode || camera.fogType == FogType.WATER
                || camera.fogType == FogType.LAVA || camera.fogType == FogType.POWDER_SNOW || camera.pos == null) return false;
        float end = distance.get();
        float start = end * (1 - MAX_THICKNESS * thickness.get() / 100f);
        float brightness = night.get() ? brightness(camera) : 1;
        int accent = ThemeColors.primary();
        int second = ThemeColors.secondary();
        inverse.set(projection).mul(camera.viewRotationMatrix).invert().get(inverseData);
        float time = (float) (((now - epoch) * 1.0e-9) % 3600);
        float zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0;
        PostEffectUtils.writeUniform(chain, "ParadoxAmbienceInverse", inverseData);
        PostEffectUtils.writeUniform(chain, "ParadoxAmbienceConfig", start, end, MAX_BLUR * blur.get() / 100f, zeroToOne,
                strength * opacity.get() / 100f, clouds.get() / 100f, time, translucent ? 1 : 0);
        PostEffectUtils.writeUniform(chain, "ParadoxAmbienceTint",
                pastel(ARGB.redFloat(accent)) * brightness, pastel(ARGB.greenFloat(accent)) * brightness,
                pastel(ARGB.blueFloat(accent)) * brightness, 1,
                (float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z, 0,
                pastel(ARGB.redFloat(second)) * brightness, pastel(ARGB.greenFloat(second)) * brightness,
                pastel(ARGB.blueFloat(second)) * brightness, 1);
        return true;
    }

    private static float pastel(float channel) {
        return channel + (1 - channel) * PASTEL;
    }

    private static float brightness(CameraRenderState camera) {
        var fog = camera.fogData;
        if (fog == null || fog.color == null) return 1;
        var color = fog.color;
        float luminance = 0.2126f * color.x + 0.7152f * color.y + 0.0722f * color.z;
        return Math.clamp(luminance / 0.6f, 0.12f, 1);
    }

    @Override
    protected void onEnable() {
        lastFrame = 0;
    }
}
