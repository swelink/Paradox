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

public final class FogModule extends Module {
    private static final float FADE_SPEED = 6;
    private static final float SKY_STRENGTH = 1;
    private static final float MAX_THICKNESS = 0.8f;
    private static final float ACCENT_MIX = 0.8f;
    private final IntSetting distance = addSetting(new IntSetting("Distance", 64, 8, 256) {
        @Override
        protected String formatValue(int value) {
            return value + " blocks";
        }
    });
    private final PercentSetting thickness = addSetting(new PercentSetting("Thickness", 75));
    private final PercentSetting opacity = addSetting(new PercentSetting("Opacity", 100));
    private final BooleanSetting sky = addSetting(new BooleanSetting("Tint Sky", true));
    private final BooleanSetting night = addSetting(new BooleanSetting("Darken At Night", true));
    private final Matrix4f inverse = new Matrix4f();
    private final float[] inverseData = new float[16];
    private float strength;
    private long lastFrame;

    public FogModule() {
        super("Fog", "Accent colored fog in the distance", Category.RENDER);
    }

    public boolean rendering() {
        return isEnabled() || strength > 0;
    }

    public boolean configure(PostChain chain, CameraRenderState camera, Matrix4f projection) {
        long now = System.nanoTime();
        float delta = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) * 1.0e-9f);
        lastFrame = now;
        float target = isEnabled() ? 1 : 0;
        strength += (target - strength) * (1 - (float) Math.exp(-FADE_SPEED * delta));
        if (Math.abs(target - strength) < 0.001f) strength = target;
        if (strength <= 0 || camera.isPanoramicMode || camera.fogType == FogType.WATER
                || camera.fogType == FogType.LAVA || camera.fogType == FogType.POWDER_SNOW) return false;
        float end = distance.get();
        float start = end * (1 - MAX_THICKNESS * thickness.get() / 100f);
        float brightness = night.get() ? brightness(camera) : 1;
        int accent = ThemeColors.primary();
        var vanilla = camera.fogData == null ? null : camera.fogData.color;
        inverse.set(projection).mul(camera.viewRotationMatrix).invert().get(inverseData);
        PostEffectUtils.writeUniform(chain, "ParadoxFogInverse", inverseData);
        PostEffectUtils.writeUniform(chain, "ParadoxFogTint",
                blend(ARGB.redFloat(accent) * brightness, vanilla == null ? 0 : vanilla.x, vanilla != null),
                blend(ARGB.greenFloat(accent) * brightness, vanilla == null ? 0 : vanilla.y, vanilla != null),
                blend(ARGB.blueFloat(accent) * brightness, vanilla == null ? 0 : vanilla.z, vanilla != null),
                strength * opacity.get() / 100f);
        PostEffectUtils.writeUniform(chain, "ParadoxFogConfig", start, end, sky.get() ? SKY_STRENGTH : 0,
                RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0);
        return true;
    }

    private static float blend(float accent, float sky, boolean hasSky) {
        return hasSky ? accent * ACCENT_MIX + sky * (1 - ACCENT_MIX) : accent;
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
