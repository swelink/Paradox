package cc.swelink.systems.modules.movement;

import cc.swelink.Paradox;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.combat.KillauraModule;
import cc.swelink.systems.modules.movement.speed.SpeedModule;
import cc.swelink.utils.combat.ShieldBlockUtils;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.OrbitMovement;
import cc.swelink.utils.player.PushOrbitPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class TargetStrafeModule extends Module {
    private final BooleanSetting ring = addSetting(new BooleanSetting("Ring", true));
    private final IntSetting distance = addSetting(new IntSetting("Distance", 25, 5, 60) {
        @Override
        protected String formatValue(int value) {
            return String.format(Locale.ROOT, "%.1f blocks", value / 10f);
        }
    });
    private final BooleanSetting optimalSpeed = addSetting(new BooleanSetting("Optimal speed", false));
    private final BooleanSetting invisible = addSetting(new BooleanSetting("Invisible", false));
    private static final int TRACK_WINDOW = 4;
    private final Vec3[] history = new Vec3[2 * TRACK_WINDOW + 1];
    private final double[] driftX = new double[PushOrbitPlanner.horizon() + 1];
    private final double[] driftZ = new double[PushOrbitPlanner.horizon() + 1];
    private Player trackedTarget;
    private int trackedTick;
    private int trackedCount;
    private int direction = 1;
    private int plannedTick = Integer.MIN_VALUE;
    private int plannedX;
    private int plannedZ;
    private LocalPlayer steeringPlayer;
    private Player steeringTarget;

    public TargetStrafeModule() {
        super("Target Strafe", "Circles your target", Category.MOVEMENT);
    }

    public Input remap(Minecraft client, Input input) {
        steeringPlayer = null;
        steeringTarget = null;
        plannedTick = Integer.MIN_VALUE;
        var player = client.player;
        if (!isEnabled() || player == null || client.level == null || !player.isAlive()
                || player.isSpectator() || player.isPassenger() || player.isFallFlying()
                || player.getAbilities().flying || player.isInWater() || player.isInLava()
                || player.onClimbable() || input.shift() || !Gameplay.focused(client)
                || input.forward() == input.backward() && input.left() == input.right()) return null;
        var target = Paradox.MODULES.get(KillauraModule.class).target(client);
        if (target == null) return null;
        track(player, target);
        if (input.left() != input.right()) direction = input.left() ? 1 : -1;
        double x = target.getX() - player.getX();
        double z = target.getZ() - player.getZ();
        double radius = Math.hypot(x, z);
        if (radius < 0.001) return null;
        steeringPlayer = player;
        steeringTarget = target;
        var speed = Paradox.MODULES.get(SpeedModule.class);
        if (speed.customActive(client)) return null;
        double yaw = Math.toRadians(Paradox.ROTATIONS.movementYaw(player.getYRot()));
        double look = Math.toRadians(ShieldBlockUtils.latestHeadYaw(target));
        double behindX = invisible.get() ? Math.sin(look) : 0;
        double behindZ = invisible.get() ? -Math.cos(look) : 0;
        if (optimalSpeed.get()) {
            var window = speed.grimPushWindow(client);
            if (window != null) {
                if (!window.available()) {
                    int lead = Math.clamp(Math.round(radius / 0.3), 0, driftX.length - 1);
                    return keys(input, yaw, x + driftX[(int) lead], z + driftZ[(int) lead]);
                }
                var plan = PushOrbitPlanner.plan(player, speed.grimContactBoxes(target), driftX, driftZ, window.room(),
                        true, window.lastTick(), input.jump(), yaw, direction, behindX, behindZ);
                if (plan != null) {
                    plannedTick = player.tickCount;
                    plannedX = plan.pushX();
                    plannedZ = plan.pushZ();
                    return new Input(plan.forward() > 0, plan.forward() < 0, plan.sideways() > 0, plan.sideways() < 0,
                            input.jump(), input.shift(), input.sprint() && plan.forward() > 0);
                }
            }
        }
        x /= radius;
        z /= radius;
        Vec3 velocity = player.getDeltaMovement();
        double outward = -(velocity.x * x + velocity.z * z);
        double correction = Math.clamp((radius + outward * 4 - distance.get() / 10.0) * 1.8, -1, 1);
        if (invisible.get()) {
            double offset = Mth.wrapDegrees(Math.toDegrees(Math.atan2(behindZ, behindX) - Math.atan2(-z, -x)));
            double side = Math.signum(offset) * Math.min(1, Math.abs(offset) / 30);
            double worldX = z * side + x * correction;
            double worldZ = -x * side + z * correction;
            if (Math.hypot(worldX, worldZ) < 0.2) {
                return new Input(false, false, false, false, input.jump(), input.shift(), false);
            }
            return keys(input, yaw, worldX, worldZ);
        }
        return keys(input, yaw, z * direction + x * correction, -x * direction + z * correction);
    }

    private void track(LocalPlayer player, Player target) {
        var interpolation = target.getInterpolation();
        Vec3 latest = interpolation != null && interpolation.hasActiveInterpolation() && interpolation.target() != null
                ? interpolation.target().position() : target.position();
        if (target != trackedTarget || player.tickCount != trackedTick + 1) {
            trackedTarget = target;
            trackedCount = 0;
        }
        trackedTick = player.tickCount;
        System.arraycopy(history, 0, history, 1, history.length - 1);
        history[0] = latest;
        trackedCount = Math.min(trackedCount + 1, history.length);
        java.util.Arrays.fill(driftX, 0);
        java.util.Arrays.fill(driftZ, 0);
        if (trackedCount < TRACK_WINDOW + 1) return;
        double vx = (history[0].x - history[TRACK_WINDOW].x) / TRACK_WINDOW;
        double vz = (history[0].z - history[TRACK_WINDOW].z) / TRACK_WINDOW;
        double turn = 0;
        if (trackedCount >= 2 * TRACK_WINDOW + 1) {
            double px = (history[TRACK_WINDOW].x - history[2 * TRACK_WINDOW].x) / TRACK_WINDOW;
            double pz = (history[TRACK_WINDOW].z - history[2 * TRACK_WINDOW].z) / TRACK_WINDOW;
            if (Math.hypot(vx, vz) > 0.03 && Math.hypot(px, pz) > 0.03) {
                turn = Math.clamp(Math.atan2(px * vz - pz * vx, px * vx + pz * vz) / TRACK_WINDOW, -0.35, 0.35);
            }
        }
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        double x = 0;
        double z = 0;
        for (int k = 1; k < driftX.length; k++) {
            double rx = vx * cos - vz * sin;
            vz = vx * sin + vz * cos;
            vx = rx;
            x += vx;
            z += vz;
            driftX[k] = x;
            driftZ[k] = z;
        }
    }

    private static Input keys(Input input, double yaw, double worldX, double worldZ) {
        double length = Math.hypot(worldX, worldZ);
        double sideways = (worldX * Math.cos(yaw) + worldZ * Math.sin(yaw)) / length;
        double forward = (worldZ * Math.cos(yaw) - worldX * Math.sin(yaw)) / length;
        double threshold = Math.sin(Math.PI / 8);
        return new Input(forward > threshold, forward < -threshold,
                sideways > threshold, sideways < -threshold,
                input.jump(), input.shift(), input.sprint() && forward > threshold);
    }

    public Vec3 plannedPush(LocalPlayer player) {
        return isEnabled() && player == steeringPlayer && plannedTick == player.tickCount
                ? new Vec3(plannedX, 0, plannedZ) : null;
    }

    public Vec3 customMovement(Minecraft client, double speed) {
        if (!isEnabled() || steeringPlayer == null || steeringPlayer != client.player || steeringTarget == null
                || steeringTarget.level() != client.level || !steeringTarget.isAlive() || steeringTarget.isRemoved()
                || !Paradox.MODULES.get(KillauraModule.class).isEnabled()) return null;
        return OrbitMovement.step(steeringPlayer.position().subtract(steeringTarget.position()),
                distance.get() / 10.0, speed, direction);
    }

    public Player ringTarget(Minecraft client) {
        if (!isEnabled() || !ring.get() || client.player == null || client.level == null
                || client.gui.hud.isHidden()) return null;
        return Paradox.MODULES.get(KillauraModule.class).target(client);
    }

    public float orbitRadius() {
        return distance.get() / 10f;
    }

    @Override
    protected void onDisable() {
        direction = 1;
        plannedTick = Integer.MIN_VALUE;
        trackedTarget = null;
        steeringPlayer = null;
        steeringTarget = null;
    }
}
