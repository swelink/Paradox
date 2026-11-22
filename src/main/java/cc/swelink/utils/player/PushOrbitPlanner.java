package cc.swelink.utils.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class PushOrbitPlanner {
    private static final int HORIZON = 5;
    private static final double REACH = 0.199;
    private static final double AIR_FRICTION = 0.91;
    private static final double MARGIN = 0.06;
    private static final double BEHIND_WEIGHT = 1;
    private static final double TRAVEL_WEIGHT = 0.35;
    private static final int[][] PUSHES = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    public record Plan(int pushX, int pushZ, int forward, int sideways) {
    }

    private final double loX, hiX, loZ, hiZ, centerX, centerZ, half;
    private final double room, firstAccel, firstFriction, airAccel;
    private final int direction;
    private final int[] keyForward = new int[8];
    private final int[] keySideways = new int[8];
    private final double[] keyX = new double[8];
    private final double[] keyZ = new double[8];
    private double[] driftX = new double[HORIZON + 1];
    private double[] driftZ = new double[HORIZON + 1];
    private double behindX;
    private double behindZ;
    private double boostX;
    private double boostZ;
    private int bestFirst = -1;
    private int bestSurvival = -1;
    private double bestScore = Double.NEGATIVE_INFINITY;

    private PushOrbitPlanner(double loX, double hiX, double loZ, double hiZ, double half, double room,
                             double firstAccel, double firstFriction, double airAccel, double yaw, int direction) {
        this.loX = loX;
        this.hiX = hiX;
        this.loZ = loZ;
        this.hiZ = hiZ;
        this.centerX = (loX + hiX) / 2;
        this.centerZ = (loZ + hiZ) / 2;
        this.half = half;
        this.room = room;
        this.firstAccel = firstAccel;
        this.firstFriction = firstFriction;
        this.airAccel = airAccel;
        this.direction = direction;
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double threshold = Math.sin(Math.PI / 8);
        for (int i = 0; i < 8; i++) {
            double length = Math.hypot(PUSHES[i][0], PUSHES[i][1]);
            double wx = PUSHES[i][0] / length;
            double wz = PUSHES[i][1] / length;
            double sideways = wx * cos + wz * sin;
            double forward = wz * cos - wx * sin;
            int f = forward > threshold ? 1 : forward < -threshold ? -1 : 0;
            int s = sideways > threshold ? 1 : sideways < -threshold ? -1 : 0;
            keyForward[i] = f;
            keySideways[i] = s;
            double kx = s * cos - f * sin;
            double kz = f * cos + s * sin;
            double keyLength = Math.hypot(kx, kz);
            keyX[i] = keyLength < 1.0e-6 ? 0 : kx / keyLength;
            keyZ[i] = keyLength < 1.0e-6 ? 0 : kz / keyLength;
        }
    }

    public static int horizon() {
        return HORIZON;
    }

    public static Plan plan(LocalPlayer player, List<AABB> targetBoxes, double[] driftX, double[] driftZ,
                            double room, boolean available, boolean lastTick, boolean jump, double yaw, int direction,
                            double behindX, double behindZ) {
        if (targetBoxes.isEmpty()) return null;
        double loX = Double.NEGATIVE_INFINITY, hiX = Double.POSITIVE_INFINITY;
        double loY = Double.NEGATIVE_INFINITY, hiY = Double.POSITIVE_INFINITY;
        double loZ = Double.NEGATIVE_INFINITY, hiZ = Double.POSITIVE_INFINITY;
        for (AABB box : targetBoxes) {
            loX = Math.max(loX, box.minX);
            hiX = Math.min(hiX, box.maxX);
            loY = Math.max(loY, box.minY);
            hiY = Math.min(hiY, box.maxY);
            loZ = Math.max(loZ, box.minZ);
            hiZ = Math.min(hiZ, box.maxZ);
        }
        AABB body = player.getBoundingBox();
        if (body.minY - REACH >= hiY || body.maxY + REACH <= loY) return null;
        double half = body.getXsize() / 2 + REACH - MARGIN;
        double firstAccel = 0.98 * accel(player);
        double firstFriction = player.onGround() ? friction(player) * AIR_FRICTION : AIR_FRICTION;
        double airAccel = 0.98 * (player.isSprinting() ? 0.026 : 0.02);
        var planner = new PushOrbitPlanner(loX, hiX, loZ, hiZ, half, room, firstAccel, firstFriction, airAccel,
                yaw, direction);
        planner.behindX = behindX;
        planner.behindZ = behindZ;
        planner.driftX = driftX;
        planner.driftZ = driftZ;
        Vec3 velocity = player.getDeltaMovement();
        if (jump && player.onGround() && player.isSprinting()) {
            planner.boostX = -Math.sin(yaw) * 0.2;
            planner.boostZ = Math.cos(yaw) * 0.2;
        }
        planner.search(0, -1, player.getX(), player.getZ(), velocity.x, velocity.z, available, lastTick, 0);
        if (planner.bestFirst < 0) return null;
        int i = planner.bestFirst;
        return new Plan(PUSHES[i][0], PUSHES[i][1], planner.keyForward[i], planner.keySideways[i]);
    }

    private void search(int depth, int first, double x, double z, double vx, double vz,
                        boolean available, boolean previous, double score) {
        if (depth > 0 && !available) {
            int ahead = Math.min(depth + 1, HORIZON);
            leaf(first, depth, -Math.hypot(x + vx - centerX - driftX[ahead], z + vz - centerZ - driftZ[ahead]));
            return;
        }
        if (depth == HORIZON) {
            leaf(first, previous || reachable(x, z, vx, vz) ? HORIZON + 1 : HORIZON, score);
            return;
        }
        double accel = depth == 0 ? firstAccel : airAccel;
        double friction = depth == 0 ? firstFriction : AIR_FRICTION;
        double push = available ? room : 0;
        for (int i = 0; i < 8; i++) {
            double mx = vx + keyX[i] * accel + PUSHES[i][0] * push;
            double mz = vz + keyZ[i] * accel + PUSHES[i][1] * push;
            if (depth == 0 && keyForward[i] > 0) {
                mx += boostX;
                mz += boostZ;
            }
            double nx = x + mx;
            double nz = z + mz;
            boolean contact = touches(depth, x, z, nx, nz);
            double rx = x - centerX - driftX[depth];
            double rz = z - centerZ - driftZ[depth];
            double radius = Math.hypot(rx, rz);
            double gained = TRAVEL_WEIGHT * Math.hypot(mx, mz);
            if (behindX != 0 || behindZ != 0) {
                double ax = nx - centerX - driftX[depth + 1];
                double az = nz - centerZ - driftZ[depth + 1];
                double after = Math.hypot(ax, az);
                gained += after < 0.05 ? 0 : BEHIND_WEIGHT * (ax * behindX + az * behindZ) / after;
            } else if (radius >= 0.05) {
                gained += (-rz * mx + rx * mz) * direction / radius;
            }
            search(depth + 1, depth == 0 ? i : first, nx, nz, mx * friction, mz * friction,
                    contact || previous, contact, score + gained);
        }
    }

    private boolean reachable(double x, double z, double vx, double vz) {
        for (int i = 0; i < 8; i++) {
            double mx = vx + keyX[i] * airAccel + PUSHES[i][0] * room;
            double mz = vz + keyZ[i] * airAccel + PUSHES[i][1] * room;
            if (touches(HORIZON, x, z, x + mx, z + mz)) return true;
        }
        return false;
    }

    private boolean touches(int depth, double x, double z, double nx, double nz) {
        double ox = driftX[depth];
        double oz = driftZ[depth];
        return Math.min(x, nx) - half < hiX + ox && Math.max(x, nx) + half > loX + ox
                && Math.min(z, nz) - half < hiZ + oz && Math.max(z, nz) + half > loZ + oz;
    }

    private void leaf(int first, int survival, double score) {
        if (first < 0) return;
        if (survival > bestSurvival || survival == bestSurvival && score > bestScore) {
            bestSurvival = survival;
            bestScore = score;
            bestFirst = first;
        }
    }

    private static double friction(LocalPlayer player) {
        return player.level().getBlockState(player.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction();
    }

    private static double accel(LocalPlayer player) {
        if (player.onGround()) {
            double slip = friction(player);
            return player.getSpeed() * (0.21600002 / (slip * slip * slip));
        }
        return player.isSprinting() ? 0.026 : 0.02;
    }
}
