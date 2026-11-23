package cc.swelink.utils.rotation;

import cc.swelink.Paradox;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.Setting;

import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public final class RotationSettings {
    private static final float MAX_DEGREES_PER_TICK = 180;
    private final IntSetting speed = new IntSetting("Speed", 100, 1, 100).bounded();
    private final ModeSetting rotationMode = new ModeSetting("Rotation", "Linear", "Interpolated", "Linear");
    private final BooleanSetting silent = new BooleanSetting("Silent", true);
    private final MoveFixSettings correction;

    public RotationSettings(Consumer<Setting<?>> register) {
        register.accept(speed);
        register.accept(rotationMode);
        register.accept(silent);
        correction = new MoveFixSettings(register, silent::get);
    }

    public boolean silent() {
        return silent.get();
    }

    public void request(Minecraft client, Rotation target, int priority) {
        request(client, target, priority, correction.get());
    }

    public void requestGlide(Minecraft client, Rotation target, int priority) {
        MoveFix configured = correction.get();
        request(client, target, priority, configured == MoveFix.OFF ? MoveFix.SILENT : configured);
    }

    private void request(Minecraft client, Rotation target, int priority, MoveFix movement) {
        Rotation rotation = interpolate(client, target);
        if (silent.get()) {
            Paradox.ROTATIONS.requestSilent(rotation, degreesPerTick(), priority, movement);
        } else {
            Paradox.ROTATIONS.request(rotation, degreesPerTick(), priority);
        }
    }

    public void holdLocalRotation(Minecraft client) {
        Paradox.ROTATIONS.holdLocalRotation(client, silent.get() ? correction.get() : MoveFix.OFF);
    }

    public Rotation current(Minecraft client) {
        Rotation current = silent.get() ? Paradox.ROTATIONS.packetRotation() : null;
        if (current == null && silent.get()) {
            current = Paradox.ROTATIONS.serverRotation();
        }
        return current != null ? current : new Rotation(client.player.getYRot(), client.player.getXRot());
    }

    public Rotation interpolate(Minecraft client, Rotation target) {
        if (rotationMode.get().equals("Linear")) {
            return target;
        }
        Rotation current = current(client);
        float yawDelta = RotationUtils.yawDifference(current.yaw(), target.yaw());
        float pitchDelta = target.pitch() - current.pitch();
        float yawStep = rotationStep(yawDelta);
        float pitchStep = rotationStep(pitchDelta);
        if (silent.get()) {
            var snapped = RotationUtils.snapToSensitivity(
                    new Rotation(current.yaw() + yawStep, current.pitch() + pitchStep), current);
            var snappedTarget = RotationUtils.snapToSensitivity(target, current);
            float minimumStep = (float) RotationUtils.sensitivityStep();
            if (snapped.yaw() == current.yaw() && snappedTarget.yaw() != current.yaw()) {
                yawStep = Math.copySign(minimumStep, yawDelta);
            }
            if (snapped.pitch() == current.pitch() && snappedTarget.pitch() != current.pitch()) {
                pitchStep = Math.copySign(minimumStep, pitchDelta);
            }
        }
        return new Rotation(current.yaw() + yawStep, current.pitch() + pitchStep);
    }

    private float rotationStep(float difference) {
        return Math.abs(difference) <= 0.001f ? difference : difference * (speed.get() / 100f);
    }

    private float degreesPerTick() {
        return MAX_DEGREES_PER_TICK * speed.get() / 100f;
    }
}
