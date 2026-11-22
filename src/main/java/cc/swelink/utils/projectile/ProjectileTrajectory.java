package cc.swelink.utils.projectile;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ProjectileTrajectory {
    private ProjectileTrajectory() {
    }

    public interface Environment {
        boolean loaded(Vec3 position);
        boolean water(Vec3 position);
        Impact collide(Vec3 start, Vec3 end, int tick);
    }

    public record Impact(Vec3 position, Vec3 normal) {
    }

    public record Path(List<Vec3> points, Impact impact, double ticks) {
    }

    public static Path trace(Vec3 position, Vec3 velocity, boolean arrow, int maxTicks, Environment world) {
        return trace(position, velocity, arrow, arrow ? 0.05 : 0.03, maxTicks, world);
    }

    public static Path trace(Vec3 position, Vec3 velocity, boolean arrow, double gravity,
                             int maxTicks, Environment world) {
        var points = new ArrayList<Vec3>();
        points.add(position);
        int elapsed = 0;
        for (int tick = 0; tick < maxTicks; tick++) {
            if (!world.loaded(position)) break;
            boolean water = world.water(position);
            if (arrow) {
                if (water) velocity = velocity.scale(0.6f);
            } else {
                velocity = velocity.add(0, -gravity, 0).scale(water ? 0.8f : 0.99f);
            }
            Vec3 end = position.add(velocity);
            if (!world.loaded(end)) break;
            Impact impact = world.collide(position, end, tick);
            if (impact != null) {
                double fraction = velocity.lengthSqr() > 1.0e-12
                        ? Math.clamp(position.distanceTo(impact.position()) / velocity.length(), 0, 1) : 0;
                points.add(impact.position());
                return new Path(List.copyOf(points), impact, tick + fraction);
            }
            points.add(end);
            position = end;
            elapsed++;
            if (arrow) velocity = (water ? velocity : velocity.scale(0.99f)).add(0, -gravity, 0);
        }
        return new Path(List.copyOf(points), null, elapsed);
    }
}
