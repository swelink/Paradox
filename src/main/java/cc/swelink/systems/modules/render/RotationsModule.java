package cc.swelink.systems.modules.render;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class RotationsModule extends Module {
    private LocalPlayer trackedPlayer;
    private float previousBodyYaw;
    private float bodyYaw;
    private float previousHeadYaw;
    private float headYaw;
    private float previousPitch;
    private float pitch;
    private boolean silent;
    private boolean wasSilent;

    public RotationsModule() {
        super("Rotations", "Shows silent rotations", Category.RENDER);
    }

    public void capture(Minecraft client) {
        var player = client.player;
        if (!isEnabled() || player == null || client.level == null) {
            trackedPlayer = null;
            silent = wasSilent = false;
            return;
        }
        if (trackedPlayer != player) {
            trackedPlayer = player;
            bodyYaw = player.yBodyRotO;
            headYaw = player.yHeadRotO;
            pitch = player.xRotO;
            silent = false;
        }
        previousBodyYaw = bodyYaw;
        previousHeadYaw = headYaw;
        previousPitch = pitch;
        wasSilent = silent;
        var rotation = Paradox.ROTATIONS.packetRotation();
        silent = rotation != null;
        bodyYaw = silent ? rotation.yaw() : player.yBodyRot;
        headYaw = silent ? rotation.yaw() : player.yHeadRot;
        pitch = silent ? rotation.pitch() : player.getXRot();
    }

    public void applyRenderState(LivingEntity entity, LivingEntityRenderState state, float tickDelta) {
        if (!isEnabled()) {
            return;
        }
        var client = Minecraft.getInstance();
        if (entity != client.player || entity != trackedPlayer || client.options.getCameraType().isFirstPerson()
                || (!silent && !wasSilent)) {
            return;
        }

        float amount = Math.clamp(tickDelta, 0, 1);
        state.bodyRot = Mth.rotLerp(amount, previousBodyYaw, bodyYaw);
        float head = Mth.rotLerp(amount, previousHeadYaw, headYaw);
        float renderedPitch = Mth.lerp(amount, previousPitch, pitch);
        float direction = state.isUpsideDown ? -1 : 1;
        state.yRot = Mth.wrapDegrees(head - state.bodyRot) * direction;
        state.xRot = renderedPitch * direction;
    }
}
