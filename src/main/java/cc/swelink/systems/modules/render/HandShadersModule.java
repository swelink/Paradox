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
import net.minecraft.world.phys.Vec3;

public final class HandShadersModule extends Module {
    private static final float TRAIL_DEPTH = 1.5f;
    private final GlowSettings shader = new GlowSettings(this::addSetting, () -> true, 0);
    private final BooleanSetting thirdPerson = addSetting(new BooleanSetting("Third person", false));
    private final BooleanSetting trail = addSetting(new BooleanSetting("Trail", false));
    private final IntSetting trailLength = addSetting(new IntSetting("Trail length", 4, 1, 15));
    private final IntSetting trailMotion = addSetting(new IntSetting("Trail motion", 100, 0, 200));
    private float lastYaw;
    private float lastPitch;
    private Vec3 lastPosition;
    private long lastFrame;
    private final HandShaderRenderer renderer = new HandShaderRenderer();

    public HandShadersModule() {
        super("HandShaders", "ESP shaders on your hands", Category.RENDER);
        trailLength.visibleWhen(trail::get);
        trailMotion.visibleWhen(trail::get);
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
        var position = camera.position();
        float focal = 2 * (float) Math.tan(Math.toRadians(camera.getFov()) / 2);
        float aspect = client.getWindow().getWidth() / (float) Math.max(1, client.getWindow().getHeight());
        float shiftU = 0, shiftV = 0, zoom = 1;
        if (lastPosition != null) {
            float motion = trailMotion.get() / 100f;
            var moved = position.subtract(lastPosition);
            var left = camera.leftVector();
            var up = camera.upVector();
            var forward = camera.forwardVector();
            float side = (float) -(moved.x * left.x() + moved.y * left.y() + moved.z * left.z()) * motion / TRAIL_DEPTH;
            float lift = (float) (moved.x * up.x() + moved.y * up.y() + moved.z * up.z()) * motion / TRAIL_DEPTH;
            float push = (float) (moved.x * forward.x() + moved.y * forward.y() + moved.z * forward.z()) * motion / TRAIL_DEPTH;
            float turnU = (float) Math.toRadians(Mth.wrapDegrees(yaw - lastYaw));
            float turnV = (float) Math.toRadians(pitch - lastPitch);
            shiftU = (turnU + side) / (focal * aspect);
            shiftV = (lift - turnV) / focal;
            zoom = Math.clamp(1 - push, 0.85f, 1.15f);
        }
        float decay = (float) Math.exp(-delta * 3 / (trailLength.get() / 10f));
        if (delta > 0.25f || Math.abs(shiftU) > 0.5f || Math.abs(shiftV) > 0.5f) {
            decay = 0;
            shiftU = 0;
            shiftV = 0;
            zoom = 1;
        }
        lastYaw = yaw;
        lastPitch = pitch;
        lastPosition = position;
        lastFrame = now;
        PostEffectUtils.writeUniform(chain, "TrailConfig", shiftU, shiftV, zoom, decay);
    }

    @Override
    protected void onDisable() {
        renderer.requestRelease();
        lastPosition = null;
        lastFrame = 0;
    }
}
