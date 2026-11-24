package cc.swelink.systems.modules.render;

import cc.swelink.renderer.HandShaderRenderer;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.render.GlowSettings;
import cc.swelink.utils.render.PostEffectUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class HandShadersModule extends Module {
    private final GlowSettings shader = new GlowSettings(this::addSetting, () -> true, 0);
    private final BooleanSetting thirdPerson = addSetting(new BooleanSetting("Third person", false));
    private final BooleanSetting trail = addSetting(new BooleanSetting("Trail", false));
    private final IntSetting trailFade = addSetting(new IntSetting("Trail fade", 400, 1, 1000) {
        @Override
        protected String formatValue(int value) {
            return value + "ms";
        }
    });
    private float lastYaw;
    private float lastPitch;
    private long lastFrame;
    private final HandShaderRenderer renderer = new HandShaderRenderer();

    public HandShadersModule() {
        super("HandShaders", "ESP shaders on your hands", Category.RENDER);
        trailFade.visibleWhen(trail::get);
    }

    public boolean active() {
        var client = Minecraft.getInstance();
        return isEnabled() && client.player != null && client.level != null
                && client.getCameraEntity() == client.player
                && !client.player.isSpectator() && !client.player.isSleeping() && !client.gui.hud.isHidden();
    }

    public boolean handsActive() {
        return active() && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    public boolean thirdPersonActive() {
        return active() && thirdPerson.get() && !Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    public HandShaderRenderer renderer() {
        return renderer;
    }

    public void configure(PostChain chain) {
        shader.configure(chain, ThemeColors.primary());
    }

    public Identifier handChain() {
        return trail.get() ? HandShaderRenderer.TRAIL_EFFECT : HandShaderRenderer.POST_EFFECT;
    }

    public void configureHands(PostChain chain) {
        configure(chain);
        if (HandShaderRenderer.TRAIL_EFFECT.equals(chain.id())) {
            configureTrail(chain);
        }
    }

    private void configureTrail(PostChain chain) {
        var client = Minecraft.getInstance();
        var camera = client.gameRenderer.mainCamera();
        long now = System.nanoTime();
        float delta = lastFrame == 0 ? 1 : (now - lastFrame) * 1e-9f;
        float yaw = camera.yRot();
        float pitch = camera.xRot();
        float focal = 2 * (float) Math.tan(Math.toRadians(camera.getFov()) / 2);
        float aspect = client.getWindow().getWidth() / (float) Math.max(1, client.getWindow().getHeight());
        float shiftU = (float) Math.toRadians(Mth.wrapDegrees(yaw - lastYaw)) / (focal * aspect);
        float shiftV = (float) -Math.toRadians(pitch - lastPitch) / focal;
        float fade = delta * 1000 / trailFade.get();
        if (delta > 0.25f || Math.abs(shiftU) > 0.5f || Math.abs(shiftV) > 0.5f) {
            fade = 1;
            shiftU = 0;
            shiftV = 0;
        }
        lastYaw = yaw;
        lastPitch = pitch;
        lastFrame = now;
        PostEffectUtils.writeUniform(chain, "TrailConfig", shiftU, shiftV, fade, 0);
    }

    @Override
    protected void onDisable() {
        renderer.requestRelease();
        lastFrame = 0;
    }
}
