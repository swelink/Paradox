package cc.swelink.utils.rotation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public record Rotation(float yaw, float pitch) {
    public Rotation {
        yaw = Float.isFinite(yaw) ? yaw : 0;
        pitch = Float.isFinite(pitch) ? pitch : 0;
        pitch = Math.clamp(pitch, -90, 90);
    }

    public Rotation nearestTo(Rotation reference) {
        return new Rotation(reference.yaw + RotationUtils.yawDifference(reference.yaw, yaw), pitch);
    }

    public Vec3 direction() {
        return Entity.calculateViewVector(pitch, yaw);
    }
}
