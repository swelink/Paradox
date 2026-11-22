package cc.swelink.systems.modules.render;

import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class SmoothCameraModule extends Module {
    private final IntSetting smoothness = addSetting(new IntSetting("Smoothness", 5, 0, 10));
    private Vec3 position;
    private Vec3 previousTarget;
    private Entity entity;
    private ClientLevel level;
    private CameraType perspective;
    private long lastFrame;

    public SmoothCameraModule() {
        super("Smooth Camera", "Smooths the camera", Category.RENDER);
    }

    public Vec3 smooth(Camera camera, Vec3 target) {
        var client = Minecraft.getInstance();
        if (!isEnabled() || smoothness.get() == 0 || client.player == null || client.level == null
                || camera.entity() != client.player || camera.isPanoramicMode()) {
            reset();
            return target;
        }
        long now = System.nanoTime();
        double elapsed = (now - lastFrame) * 1e-9;
        CameraType currentPerspective = client.options.getCameraType();
        if (position == null || entity != camera.entity() || level != client.level
                || perspective != currentPerspective || elapsed > 0.5
                || previousTarget.distanceToSqr(target) > 256) {
            position = target;
        } else {
            double amount = -Math.expm1(-elapsed / (smoothness.get() * 0.05));
            position = position.lerp(target, Math.clamp(amount, 0, 1));
        }
        previousTarget = target;
        entity = camera.entity();
        level = client.level;
        perspective = currentPerspective;
        lastFrame = now;
        return position;
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        position = null;
        previousTarget = null;
        entity = null;
        level = null;
        perspective = null;
        lastFrame = 0;
    }
}
