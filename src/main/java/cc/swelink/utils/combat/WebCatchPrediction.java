package cc.swelink.utils.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class WebCatchPrediction {
    public record Step(AABB from, AABB to) {
    }

    private WebCatchPrediction() {
    }

    public static Vec3 behindPoint(AABB body, Vec3 movement, Vec3 awayFromAttacker) {
        Vec3 horizontal = new Vec3(movement.x, 0, movement.z);
        if (horizontal.lengthSqr() < 1.0e-4) {
            horizontal = new Vec3(awayFromAttacker.x, 0, awayFromAttacker.z);
        }
        Vec3 feet = new Vec3((body.minX + body.maxX) / 2, body.minY, (body.minZ + body.maxZ) / 2);
        return feet.add(horizontal.normalize().scale(0.8));
    }

    public static List<Step> path(AABB box, Vec3 velocity, double gravity, int waitTicks) {
        int wait = Math.clamp(waitTicks, 0, 3);
        List<Step> path = new ArrayList<>();
        for (int tick = 0; tick <= wait + 2; tick++) {
            AABB next = box.move(velocity);
            if (tick >= wait) {
                path.add(new Step(box, next));
            }
            box = next;
            velocity = new Vec3(velocity.x * 0.91, (velocity.y - gravity) * 0.98, velocity.z * 0.91);
        }
        return path;
    }

    public static boolean intersects(AABB web, Step step) {
        AABB body = step.from();
        AABB expanded = web.deflate(1.0e-7).inflate(body.getXsize() / 2,
                body.getYsize() / 2, body.getZsize() / 2);
        Vec3 from = body.getCenter();
        Vec3 to = step.to().getCenter();
        return expanded.contains(from) || expanded.clip(from, to).isPresent();
    }
}
