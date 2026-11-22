package cc.swelink.utils.player;

import net.minecraft.world.phys.Vec3;

public final class OrbitMovement {
    private OrbitMovement() {
    }

    public static Vec3 step(Vec3 offset, double desiredRadius, double speed, int direction) {
        double radius = Math.hypot(offset.x, offset.z);
        if (radius < 0.001 || speed <= 0) return Vec3.ZERO;
        double nextRadius = Math.max(0.05, radius + Math.clamp(desiredRadius - radius, -speed * 0.7, speed * 0.7));
        double cosine = Math.clamp((radius * radius + nextRadius * nextRadius - speed * speed)
                / (2 * radius * nextRadius), -1, 1);
        double sine = Math.sqrt(Math.max(0, 1 - cosine * cosine)) * direction;
        double x = offset.x / radius;
        double z = offset.z / radius;
        Vec3 movement = new Vec3((x * cosine - z * sine) * nextRadius - offset.x, 0,
                (x * sine + z * cosine) * nextRadius - offset.z);
        double length = movement.horizontalDistance();
        return length > speed ? movement.scale(speed / length) : movement;
    }
}
