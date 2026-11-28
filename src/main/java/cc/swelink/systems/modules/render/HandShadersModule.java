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
import org.joml.Matrix3f;

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
    private float lastSwayX;
    private float lastSwayY;
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
        var player = client.player;
        long now = System.nanoTime();
        float delta = lastFrame == 0 ? 1 : (now - lastFrame) * 1e-9f;
        float fade = delta > 0.25f ? 1 : delta * 1000 / trailFade.get();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float swayX = (player.getViewXRot(partial) - Mth.lerp(partial, player.xBobO, player.xBob)) * 0.1f;
        float swayY = (player.getViewYRot(partial) - Mth.lerp(partial, player.yBobO, player.yBob)) * 0.1f;
        var motion = new Matrix3f();
        if (fade < 1) {
            var current = new Matrix3f().rotateX((float) Math.toRadians(swayX)).rotateY((float) Math.toRadians(swayY));
            motion.rotateX((float) Math.toRadians(lastSwayX)).rotateY((float) Math.toRadians(lastSwayY)).mul(current.invert());
        }
        float tangent = (float) Math.tan(Math.toRadians(35));
        float aspect = client.getWindow().getWidth() / (float) Math.max(1, client.getWindow().getHeight());
        lastSwayX = swayX;
        lastSwayY = swayY;
        lastFrame = now;
        PostEffectUtils.writeUniform(chain, "TrailConfig", 0, 0, fade, 0,
                motion.m00, motion.m10, motion.m20, tangent * aspect,
                motion.m01, motion.m11, motion.m21, tangent,
                motion.m02, motion.m12, motion.m22, 0);
    }

    @Override
    protected void onDisable() {
        renderer.requestRelease();
        lastFrame = 0;
    }
}
