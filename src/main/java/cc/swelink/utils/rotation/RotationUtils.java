package cc.swelink.utils.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RotationUtils {
    private RotationUtils() {
    }

    public static float wrapDegrees(float angle) {
        if (!Float.isFinite(angle)) {
            return 0;
        }
        float wrapped = angle % 360;
        if (wrapped >= 180) {
            wrapped -= 360;
        }
        if (wrapped < -180) {
            wrapped += 360;
        }
        return wrapped;
    }

    public static float yawDifference(float current, float target) {
        return wrapDegrees(target - current);
    }

    public static Rotation lookAt(Vec3 from, Vec3 target) {
        Vec3 delta = target.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        return new Rotation(wrapDegrees(yaw), pitch);
    }

    public static Rotation stepTowards(Rotation current, Rotation target, float maxDegrees) {
        if (!(maxDegrees > 0) || !Float.isFinite(maxDegrees)) {
            return current;
        }
        float yawStep = Math.clamp(yawDifference(current.yaw(), target.yaw()), -maxDegrees, maxDegrees);
        float pitchStep = Math.clamp(target.pitch() - current.pitch(), -maxDegrees, maxDegrees);

        return new Rotation(current.yaw() + yawStep, current.pitch() + pitchStep);
    }

    public static double sensitivityStep() {
        double sensitivity = Minecraft.getInstance().options.sensitivity().get();
        double factor = sensitivity * 0.6 + 0.2;
        return factor * factor * factor * 8 * 0.15;
    }

    public static Rotation snapToSensitivity(Rotation rotation, Rotation previous) {
        double step = sensitivityStep();
        float yaw = previous.yaw() + (float) (Math.round(yawDifference(previous.yaw(), rotation.yaw()) / step) * step);
        float pitch = previous.pitch() + (float) (Math.round((rotation.pitch() - previous.pitch()) / step) * step);
        return new Rotation(yaw, pitch);
    }

    public static Vec3 nearestPoint(Vec3 point, AABB box) {
        return new Vec3(Math.clamp(point.x, box.minX, box.maxX), Math.clamp(point.y, box.minY, box.maxY),
                Math.clamp(point.z, box.minZ, box.maxZ));
    }
}
