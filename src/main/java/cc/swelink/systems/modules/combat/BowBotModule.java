package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.TargetFinder;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.projectile.BlockCollisions;
import cc.swelink.utils.projectile.ProjectileTrajectory;
import cc.swelink.utils.projectile.WeaponShot;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationSettings;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class BowBotModule extends Module {
    private static final int ROTATION_PRIORITY = 3;
    private static final int BOW_DELAY = 2;
    private static final int CROSSBOW_DELAY = 1;
    private static final int MAX_FLIGHT_TICKS = 200;
    private static final int SOLVER_STEPS = 16;
    private static final double SOLVER_TOLERANCE = 0.1;
    private static final double ARROW_DRAG = 0.99;
    private static final double HIT_MARGIN = 0.3;
    private static final double AIM_INSET = 0.1;
    private static final double AIM_SIDE = 0.2;
    private static final int AIM_ROWS = 4;

    private final BooleanSetting predict = addSetting(new BooleanSetting("Predict", true));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final RotationSettings rotations = new RotationSettings(this::addSetting);

    public BowBotModule() {
        super("BowBot", "Aims your bow at players", Category.COMBAT);
    }

    @Override
    public void tick(Minecraft client) {
        var player = client.player;
        if (!Gameplay.canAct(client)) {
            return;
        }
        var shot = WeaponShot.ready(player);
        if (shot == null) return;
        for (var target : findTargets(client)) {
            var rotation = aim(player, target, shot);
            if (rotation == null) continue;
            Paradox.TARGETS.report(target);
            rotations.request(client, rotation, ROTATION_PRIORITY);
            return;
        }
    }

    private List<Player> findTargets(Minecraft client) {
        var eyes = client.player.getEyePosition();
        return TargetFinder.sorted(client, raycast.target(), target -> true,
                target -> TargetFinder.hitboxDistanceSqr(eyes, target));
    }

    private Rotation aim(LocalPlayer player, Player target, WeaponShot shot) {
        int delay = shot.bow() ? BOW_DELAY : CROSSBOW_DELAY;
        var start = player.getEyePosition().add(0, -WeaponShot.SPAWN_OFFSET, 0)
                .add(WeaponShot.motion(player).scale(delay));
        var flight = new Flight(shot, start, shot.inherited(player),
                predict.get() ? WeaponShot.motion(target).multiply(1, 0, 1) : Vec3.ZERO, delay);
        for (var point : aimPoints(target.getBoundingBox(), start)) {
            var rotation = solve(flight, point);
            if (rotation != null && reaches(player, target, flight, rotation)) return rotation;
        }
        return null;
    }

    private static List<Vec3> aimPoints(AABB box, Vec3 start) {
        var center = box.getCenter();
        double low = box.minY + AIM_INSET;
        double high = box.maxY - AIM_INSET;
        var closest = new Vec3(center.x, Math.clamp(start.y, low, high), center.z);
        var across = new Vec3(center.z - start.z, 0, start.x - center.x);
        var side = across.lengthSqr() < 1.0e-6 ? Vec3.ZERO : across.normalize().scale(AIM_SIDE);
        var points = new ArrayList<Vec3>();
        points.add(closest);
        for (int row = 0; row <= AIM_ROWS; row++) {
            var point = new Vec3(center.x, low + (high - low) * row / AIM_ROWS, center.z);
            points.add(point);
            points.add(point.add(side));
            points.add(point.subtract(side));
        }
        points.sort(Comparator.comparingDouble(point -> point.distanceToSqr(closest)));
        return points;
    }

    private Rotation solve(Flight flight, Vec3 center) {
        var shot = flight.shot();
        var start = flight.start();
        int delay = flight.delay();
        double ticks = 0;
        double lift = 0;
        double error = Double.MAX_VALUE;
        double angle = 0;
        Vec3 point = center;
        for (int step = 0; step < SOLVER_STEPS; step++) {
            double carried = (1 - Math.pow(ARROW_DRAG, ticks)) / (1 - ARROW_DRAG);
            point = center.add(flight.lead().scale(ticks + delay)).subtract(flight.inherited().scale(carried));
            var delta = point.subtract(start);
            double distance = delta.horizontalDistance();
            angle = Math.atan2(delta.y + lift, distance);
            var path = fly(angle, distance, shot);
            if (path.impact() == null) return null;
            ticks = path.ticks();
            error = delta.y - path.impact().position().y;
            lift += error;
        }
        if (Math.abs(error) > SOLVER_TOLERANCE) return null;
        return new Rotation(RotationUtils.lookAt(start, point).yaw(), (float) -Math.toDegrees(angle));
    }

    private boolean reaches(LocalPlayer player, Player target, Flight flight, Rotation rotation) {
        var shot = flight.shot();
        var box = target.getBoundingBox().inflate(HIT_MARGIN);
        var hit = new boolean[1];
        var velocity = rotation.direction().scale(shot.speed()).add(flight.inherited());
        ProjectileTrajectory.trace(flight.start(), velocity, true, shot.gravity(), MAX_FLIGHT_TICKS,
                new BlockCollisions(player.level(), player) {
                    @Override
                    public ProjectileTrajectory.Impact collide(Vec3 from, Vec3 to, int tick) {
                        var block = super.collide(from, to, tick);
                        var moved = box.move(flight.lead().scale(tick + flight.delay())).expandTowards(flight.lead());
                        var entry = moved.contains(from) ? Optional.of(from)
                                : moved.clip(from, block == null ? to : block.position());
                        if (entry.isEmpty()) return block;
                        hit[0] = true;
                        return new ProjectileTrajectory.Impact(entry.get(), new Vec3(0, 1, 0));
                    }
                });
        return hit[0];
    }

    private ProjectileTrajectory.Path fly(double angle, double distance, WeaponShot shot) {
        var velocity = new Vec3(Math.cos(angle) * shot.speed(), Math.sin(angle) * shot.speed(), 0);
        return ProjectileTrajectory.trace(Vec3.ZERO, velocity, true, shot.gravity(), MAX_FLIGHT_TICKS,
                new ProjectileTrajectory.Environment() {
                    @Override
                    public boolean loaded(Vec3 position) {
                        return true;
                    }

                    @Override
                    public boolean water(Vec3 position) {
                        return false;
                    }

                    @Override
                    public ProjectileTrajectory.Impact collide(Vec3 from, Vec3 to, int tick) {
                        if (to.x < distance) return null;
                        double fraction = (distance - from.x) / (to.x - from.x);
                        return new ProjectileTrajectory.Impact(from.lerp(to, fraction), new Vec3(0, 1, 0));
                    }
                });
    }

    @Override
    protected void onDisable() {
        Paradox.ROTATIONS.clear();
    }

    private record Flight(WeaponShot shot, Vec3 start, Vec3 inherited, Vec3 lead, int delay) {
    }
}
