package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.TargetFinder;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.projectile.BlockCollisions;
import cc.swelink.utils.projectile.HeldThrow;
import cc.swelink.utils.projectile.ProjectileTrajectory;
import cc.swelink.utils.projectile.WeaponShot;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationSettings;
import cc.swelink.utils.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PearlGrapplerModule extends Module {
    private static final int ROTATION_PRIORITY = 10;
    private static final int TIMEOUT = 40;
    private static final int MAX_FLIGHT_TICKS = 100;
    private static final double PLAYER_GRAVITY = 0.08;
    private static final double PLAYER_DRAG = 0.98;
    private static final double COLLISION_INSET = 0.01;
    private static final int STALE_TICKS = 4;
    private static final int SOLVER_STEPS = 16;
    private static final double SOLVER_TOLERANCE = 0.1;
    private static final double SPEED = 1.5;
    private static final double GRAVITY = 0.03;
    private static final double DRAG = 0.99;
    private static final double SPAWN_OFFSET = 0.1;
    private static final double AIM_INSET = 0.2;
    private static final double AIM_SIDE = 0.15;
    private static final int AIM_ROWS = 4;
    private static final HeldThrow PEARL = new HeldThrow(SPEED, GRAVITY, false, 0, true, ItemStack.EMPTY);

    private final IntSetting range = addSetting(new IntSetting("Range", 32, 4, 64));
    private final IntSetting switchDelay = addSetting(new IntSetting("Switch delay", 1, 0, 20));
    private final IntSetting throwDelay = addSetting(new IntSetting("Throw delay", 0, 0, 20));
    private final IntSetting switchBackDelay = addSetting(new IntSetting("Swap back delay", 1, 1, 20));
    private final BooleanSetting predict = addSetting(new BooleanSetting("Predict", true));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final RotationSettings rotations = new RotationSettings(this::addSetting);
    private LocalPlayer player;
    private ClientLevel level;
    private int originalSlot = -1;
    private int selectedSlot = -1;
    private int ticks;
    private int switchedAt;
    private int readyAt = -1;
    private int thrownAt = -1;
    private Plan plan;
    private final Map<Integer, Track> seen = new HashMap<>();

    public PearlGrapplerModule() {
        super("PearlGrappler", "Pearls at the closest player", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        var client = Minecraft.getInstance();
        player = client.player;
        level = client.level;
        originalSlot = selectedSlot = -1;
        ticks = switchedAt = 0;
        readyAt = thrownAt = -1;
        plan = null;
        seen.clear();
        if (!valid(client)) {
            setEnabled(false);
            return;
        }
        var inventory = player.getInventory();
        int slot = InventoryUtils.findHotbar(inventory, Items.ENDER_PEARL);
        if (slot < 0 || player.getCooldowns().isOnCooldown(inventory.getItem(slot))) setEnabled(false);
    }

    public void beforeTick(Minecraft client) {
        if (isEnabled() && !valid(client)) setEnabled(false);
    }

    @Override
    public void tick(Minecraft client) {
        if (!valid(client)) {
            setEnabled(false);
            return;
        }
        ticks++;
        if (thrownAt >= 0) return;
        if (ticks > TIMEOUT) {
            setEnabled(false);
            return;
        }
        track(client);
        plan = findPlan(client);
        if (plan == null) return;
        Paradox.TARGETS.report(plan.target());
        rotations.request(client, plan.rotation(), ROTATION_PRIORITY);
    }

    @Override
    public void tickInput(Minecraft client) {
        if (!valid(client)) {
            setEnabled(false);
            return;
        }
        if (thrownAt >= 0) {
            if (ticks - thrownAt >= switchBackDelay.get()) setEnabled(false);
            return;
        }
        var inventory = player.getInventory();
        if (originalSlot < 0) {
            if (plan == null || !SlotSelection.begin(this, inventory)) return;
            originalSlot = inventory.getSelectedSlot();
            int slot = InventoryUtils.findHotbar(inventory, Items.ENDER_PEARL);
            if (slot < 0 || !SlotSelection.select(this, inventory, slot)) {
                setEnabled(false);
                return;
            }
            SlotSelection.syncCarriedItem(client);
            selectedSlot = slot;
            switchedAt = ticks;
            return;
        }
        var stack = player.getMainHandItem();
        if (!SlotSelection.owns(this, inventory) || inventory.getSelectedSlot() != selectedSlot
                || !stack.is(Items.ENDER_PEARL) || player.getCooldowns().isOnCooldown(stack)) {
            setEnabled(false);
            return;
        }
        var rotation = rotations.current(client);
        if (plan == null || ticks - switchedAt < switchDelay.get() || !reaches(plan, rotation)) {
            readyAt = -1;
            return;
        }
        if (readyAt < 0) readyAt = ticks;
        if (ticks - readyAt < throwDelay.get()) return;
        var animation = stack.getInteractAnimation();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        player.setYRot(rotation.yaw());
        player.setXRot(rotation.pitch());
        InteractionResult result;
        try {
            result = client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
        if (!(result instanceof InteractionResult.Success success)) {
            setEnabled(false);
            return;
        }
        if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            player.swing(InteractionHand.MAIN_HAND, animation, false);
        }
        thrownAt = ticks;
    }

    private Plan findPlan(Minecraft client) {
        var eyes = player.getEyePosition();
        double maxDistance = range.get() * (double) range.get();
        var targets = TargetFinder.sorted(client, raycast.target(),
                target -> eyes.distanceToSqr(target.getBoundingBox().getCenter()) <= maxDistance,
                target -> TargetFinder.hitboxDistanceSqr(eyes, target));
        var start = new Vec3(player.getX(), player.getEyeY() - SPAWN_OFFSET, player.getZ());
        var inherited = new WeaponShot(0, GRAVITY, true).inherited(player);
        int lag = predict.get() ? latencyTicks(client) + 1 : 0;
        for (var target : targets) {
            var box = latestBox(target);
            var path = predict.get() ? predictPath(client, target, box, lag + MAX_FLIGHT_TICKS + 2) : new Vec3[] {Vec3.ZERO};
            for (var point : aimPoints(box, start)) {
                var rotation = solve(start, inherited, path, lag, point);
                if (rotation == null) continue;
                var candidate = new Plan(target, box, start, path, lag, rotation);
                if (reaches(candidate, rotation)) return candidate;
            }
        }
        return null;
    }

    private void track(Minecraft client) {
        var present = new HashSet<Integer>();
        for (var target : client.level.players()) {
            if (target == player) continue;
            present.add(target.getId());
            var latest = latestBox(target).getBottomCenter();
            var track = seen.computeIfAbsent(target.getId(), ignored -> new Track());
            if (latest.equals(track.position)) continue;
            track.previous = track.position;
            track.previousTick = track.tick;
            track.position = latest;
            track.tick = ticks;
        }
        seen.keySet().retainAll(present);
    }

    private Vec3 velocity(Player target) {
        var track = seen.get(target.getId());
        if (track == null || track.previous == null) return WeaponShot.motion(target);
        if (ticks - track.tick > STALE_TICKS) return Vec3.ZERO;
        return track.position.subtract(track.previous).scale(1.0 / Math.max(1, track.tick - track.previousTick));
    }

    private Vec3[] predictPath(Minecraft client, Player target, AABB box, int length) {
        var velocity = velocity(target);
        var path = new Vec3[length + 1];
        path[0] = Vec3.ZERO;
        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;
        boolean gliding = target.isFallFlying();
        boolean grounded = !gliding && target.onGround() && vy <= 1.0e-4;
        var offset = Vec3.ZERO;
        var body = box.deflate(COLLISION_INSET);
        float step = target.maxUpStep();
        for (int tick = 1; tick <= length; tick++) {
            if (!gliding && !grounded) vy = (vy - PLAYER_GRAVITY) * PLAYER_DRAG;
            double dy = grounded ? 0 : vy;
            if (dy != 0 && !client.level.noCollision(target, body.move(offset.add(0, dy, 0)))) {
                grounded = dy < 0;
                vy = 0;
                dy = 0;
            }
            if ((vx != 0 || vz != 0) && !client.level.noCollision(target, body.move(offset.add(vx, dy, vz)))) {
                if (grounded && client.level.noCollision(target, body.move(offset.add(vx, step, vz)))) {
                    dy = step;
                    grounded = false;
                } else {
                    vx = 0;
                    vz = 0;
                }
            }
            offset = offset.add(vx, dy, vz);
            path[tick] = offset;
        }
        return path;
    }

    private static Vec3 offset(Vec3[] path, double tick) {
        if (path.length == 1 || tick <= 0) return path[0];
        int floor = (int) Math.floor(tick);
        if (floor >= path.length - 1) return path[path.length - 1];
        return path[floor].lerp(path[floor + 1], tick - floor);
    }

    private static AABB latestBox(Player target) {
        var interpolation = target.getInterpolation();
        if (interpolation == null || !interpolation.hasActiveInterpolation() || interpolation.target() == null) {
            return target.getBoundingBox();
        }
        var offset = interpolation.target().position().subtract(target.position());
        return target.getBoundingBox().move(offset);
    }

    private static int latencyTicks(Minecraft client) {
        var connection = client.getConnection();
        var info = connection == null || client.player == null ? null : connection.getPlayerInfo(client.player.getUUID());
        return info == null ? 0 : Math.max(info.getLatency(), 0) / 50;
    }

    private static List<Vec3> aimPoints(AABB box, Vec3 start) {
        var center = box.getCenter();
        double low = box.minY + AIM_INSET;
        double high = box.maxY - AIM_INSET;
        var middle = new Vec3(center.x, Math.clamp(center.y, low, high), center.z);
        var across = new Vec3(center.z - start.z, 0, start.x - center.x);
        var side = across.lengthSqr() < 1.0e-6 ? Vec3.ZERO : across.normalize().scale(AIM_SIDE);
        var points = new ArrayList<Vec3>();
        points.add(middle);
        for (int row = 0; row <= AIM_ROWS; row++) {
            var point = new Vec3(center.x, low + (high - low) * row / AIM_ROWS, center.z);
            points.add(point);
            points.add(point.add(side));
            points.add(point.subtract(side));
        }
        points.sort(Comparator.comparingDouble(point -> point.distanceToSqr(middle)));
        return points;
    }

    private static Rotation solve(Vec3 start, Vec3 inherited, Vec3[] path, int lag, Vec3 center) {
        double ticks = 0;
        double lift = 0;
        double error = Double.MAX_VALUE;
        double angle = 0;
        Vec3 point = center;
        for (int step = 0; step < SOLVER_STEPS; step++) {
            double carried = DRAG * (1 - Math.pow(DRAG, ticks + 1)) / (1 - DRAG);
            point = center.add(offset(path, ticks + lag)).subtract(inherited.scale(carried));
            var delta = point.subtract(start);
            double distance = delta.horizontalDistance();
            angle = Math.atan2(delta.y + lift, distance);
            var arc = fly(angle, distance);
            if (arc.impact() == null) return null;
            ticks = arc.ticks();
            error = delta.y - arc.impact().position().y;
            lift += error;
        }
        if (Math.abs(error) > SOLVER_TOLERANCE) return null;
        return new Rotation(RotationUtils.lookAt(start, point).yaw(), (float) -Math.toDegrees(angle));
    }

    private static ProjectileTrajectory.Path fly(double angle, double distance) {
        var velocity = new Vec3(Math.cos(angle) * SPEED, Math.sin(angle) * SPEED, 0);
        return ProjectileTrajectory.trace(Vec3.ZERO, velocity, false, GRAVITY, MAX_FLIGHT_TICKS,
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

    private boolean reaches(Plan plan, Rotation rotation) {
        var hit = new boolean[1];
        ProjectileTrajectory.trace(plan.start(), PEARL.velocity(player, rotation), false, GRAVITY, MAX_FLIGHT_TICKS,
                new BlockCollisions(player.level(), player) {
                    @Override
                    public ProjectileTrajectory.Impact collide(Vec3 from, Vec3 to, int tick) {
                        var block = super.collide(from, to, tick);
                        var here = offset(plan.path(), tick + plan.lag());
                        var next = offset(plan.path(), tick + plan.lag() + 1);
                        var moved = plan.box().move(here).expandTowards(next.subtract(here));
                        var entry = moved.contains(from) ? Optional.of(from)
                                : moved.clip(from, block == null ? to : block.position());
                        if (entry.isEmpty()) return block;
                        hit[0] = true;
                        return new ProjectileTrajectory.Impact(entry.get(), new Vec3(0, 1, 0));
                    }
                });
        return hit[0];
    }

    private boolean valid(Minecraft client) {
        return player != null && client.player == player && level != null && client.level == level
                && client.gameMode != null && player.isAlive() && !player.isSpectator()
                && !player.isUsingItem() && !player.isHandsBusy() && !player.isPassenger()
                && Gameplay.focused(client)
                && player.containerMenu == player.inventoryMenu;
    }

    @Override
    protected void onDisable() {
        var client = Minecraft.getInstance();
        boolean current = player != null && client.player == player && client.level == level;
        boolean restore = current && originalSlot >= 0 && SlotSelection.owns(this, player.getInventory())
                && player.getInventory().getSelectedSlot() == selectedSlot;
        SlotSelection.complete(this, restore ? originalSlot : -1);
        if (restore) SlotSelection.syncCarriedItem(client);
        player = null;
        level = null;
        originalSlot = selectedSlot = -1;
        readyAt = thrownAt = -1;
        plan = null;
        seen.clear();
    }

    @EventHandler(priority = 70)
    private void onPreTick(PreTickEvent event) {
        beforeTick(event.client());
    }

    private static final class Track {
        private Vec3 position;
        private Vec3 previous;
        private int tick;
        private int previousTick;
    }

    private record Plan(Player target, AABB box, Vec3 start, Vec3[] path, int lag, Rotation rotation) {
    }
}
