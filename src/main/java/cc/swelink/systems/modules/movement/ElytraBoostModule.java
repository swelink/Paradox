package cc.swelink.systems.modules.movement;

import cc.swelink.Paradox;
import cc.swelink.mixin.FireworkRocketAccessor;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class ElytraBoostModule extends Module {
    private static final double MARGIN = 0.01;
    private static final double VANILLA_ROCKET_SPEED = 1.5;
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Grim", "Grim", "Boost"));
    private final IntSetting speed = addSetting(new IntSetting("Speed", 15, 1, 30) {
        @Override
        protected String formatValue(int value) {
            return String.format(Locale.ROOT, "%d b/s", value * 2);
        }
    });
    private final BooleanSetting hover = addSetting(new BooleanSetting("Hover", false));
    private Vec3 lastMove;
    private boolean usedItem;
    private float useYaw, usePitch;
    private Rotation steer;
    private Rotation glided;

    public ElytraBoostModule() {
        super("ElytraBoost", "Faster elytra with fireworks", Category.MOVEMENT);
        hover.visibleWhen(this::grim);
    }

    public void afterMove(LocalPlayer player) {
        lastMove = isEnabled() && grim() ? player.getDeltaMovement() : null;
    }

    @Override
    protected void onDisable() {
        lastMove = null;
        usedItem = false;
        steer = null;
        glided = null;
    }

    private boolean grim() {
        return mode.get().equals("Grim");
    }

    public double rocketScale(LocalPlayer player) {
        return isEnabled() && !grim() && player.isFallFlying() ? speed.get() / 10.0 / VANILLA_ROCKET_SPEED : 1;
    }

    public Rotation boostRotation(LocalPlayer player) {
        return isEnabled() && grim() && player.isFallFlying() ? glided : null;
    }

    @Override
    public void tickInput(Minecraft client) {
        steer = null;
        LocalPlayer player = client.player;
        if (!isEnabled() || !grim() || player == null || lastMove == null || !player.isFallFlying() || !hasRocket(player)) return;
        Rotation server = Paradox.ROTATIONS.serverRotation();
        if (usedItem) {
            steer = new Rotation(useYaw, usePitch);
        } else {
            Vec3 want = want(player);
            if (want == null || want.lengthSqr() < 1.0E-6) return;
            steer = toward(want, server == null ? new Rotation(player.getYRot(), player.getXRot()) : server);
        }
        Paradox.ROTATIONS.forcePacketRotation(steer);
    }

    private static Rotation toward(Vec3 direction, Rotation server) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = horizontal < 1.0E-6 ? server.yaw() : (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(direction.y, horizontal)), -89, 89);
        Rotation snapped = RotationUtils.snapToSensitivity(new Rotation(yaw, pitch).nearestTo(server), server);
        if (snapped.yaw() == server.yaw() && snapped.pitch() == server.pitch()) {
            snapped = new Rotation(snapped.yaw() + (float) RotationUtils.sensitivityStep(), snapped.pitch());
        }
        return snapped;
    }

    public void sent(Packet<?> packet) {
        if (packet instanceof ServerboundUseItemPacket use) {
            usedItem = true;
            useYaw = use.yRot();
            usePitch = use.xRot();
        } else if (packet instanceof ServerboundMovePlayerPacket) {
            usedItem = false;
        }
    }

    public Vec3 glide(LocalPlayer player) {
        if (!isEnabled() || !grim() || lastMove == null || !player.isFallFlying() || !hasRocket(player)) {
            glided = null;
            return null;
        }
        var rotations = Paradox.ROTATIONS;
        float yaw = rotations.packetYaw(player.getYRot());
        float pitch = rotations.packetPitch(player.getXRot());
        Rotation server = rotations.serverRotation();
        if (usedItem) {
            yaw = useYaw;
            pitch = usePitch;
        } else if (steer != null) {
            yaw = steer.yaw();
            pitch = steer.pitch();
        } else if (server != null && yaw == server.yaw() && pitch == server.pitch()) {
            yaw += (float) RotationUtils.sensitivityStep();
        }
        glided = new Rotation(yaw, pitch);
        rotations.forcePacketRotation(glided);

        boolean turns = server == null || yaw != server.yaw() || pitch != server.pitch();
        Rotation previous = turns ? server : rotations.previousServerRotation();
        Vec3 current = new Rotation(yaw, pitch).direction();
        Vec3 last = previous == null ? current : previous.direction();

        Vec3 start = lastMove;
        Vec3 predicted = glideStep(player, start, yaw, pitch);
        double[] low = new double[3], high = new double[3];
        axis(0, predicted.x, start.x, current.x, last.x, low, high);
        axis(1, predicted.y, start.y, current.y, last.y, low, high);
        axis(2, predicted.z, start.z, current.z, last.z, low, high);

        Vec3 want = want(player);
        return want == null ? null : fit(want, low, high);
    }

    private Vec3 want(LocalPlayer player) {
        var options = Minecraft.getInstance().options;
        Vec3 look = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        Vec3 side = Vec3.directionFromRotation(0, player.getYRot() - 90);
        Vec3 direction = Vec3.ZERO;
        if (options.keyUp.isDown()) direction = direction.add(look);
        if (options.keyDown.isDown()) direction = direction.subtract(look);
        if (options.keyLeft.isDown()) direction = direction.add(side);
        if (options.keyRight.isDown()) direction = direction.subtract(side);
        if (options.keyJump.isDown()) direction = direction.add(0, 1, 0);
        if (options.keyShift.isDown()) direction = direction.add(0, -1, 0);
        if (direction.lengthSqr() < 1.0E-6) return hover.get() ? Vec3.ZERO : null;
        return direction.normalize().scale(speed.get() / 10.0);
    }

    private static void axis(int index, double predicted, double start, double current, double last, double[] low, double[] high) {
        double boxLow = Math.max(-1.7, 1.7 * (Math.min(0, current) + Math.min(0, last)));
        double boxHigh = Math.min(1.7, 1.7 * (Math.max(0, current) + Math.max(0, last)));
        low[index] = predicted + Math.min(0, boxLow - start) + MARGIN;
        high[index] = predicted + Math.max(0, boxHigh - start) - MARGIN;
        if (low[index] > high[index]) low[index] = high[index] = predicted;
    }

    private static Vec3 fit(Vec3 want, double[] low, double[] high) {
        double[] wanted = {want.x, want.y, want.z};
        double scale = 1;
        boolean scalable = true;
        for (int i = 0; i < 3; i++) {
            if (low[i] > 0 || high[i] < 0) {
                scalable = false;
                break;
            }
            if (wanted[i] > high[i]) scale = Math.min(scale, high[i] / wanted[i]);
            else if (wanted[i] < low[i]) scale = Math.min(scale, low[i] / wanted[i]);
        }
        if (scalable) return new Vec3(wanted[0] * scale, wanted[1] * scale, wanted[2] * scale);
        return new Vec3(Mth.clamp(wanted[0], low[0], high[0]), Mth.clamp(wanted[1], low[1], high[1]),
                Mth.clamp(wanted[2], low[2], high[2]));
    }

    public static boolean hasRocket(LocalPlayer player) {
        return !player.level().getEntitiesOfClass(FireworkRocketEntity.class, player.getBoundingBox().inflate(4),
                rocket -> ((FireworkRocketAccessor) rocket).paradox$attachedToEntity() == player && !rocket.isRemoved()).isEmpty();
    }

    private static Vec3 glideStep(LocalPlayer player, Vec3 velocity, float yaw, float pitch) {
        double gravity = velocity.y <= 0 && player.hasEffect(MobEffects.SLOW_FALLING)
                ? Math.min(player.getGravity(), 0.01) : player.getGravity();
        Vec3 look = new Rotation(yaw, pitch).direction();
        float pitchRadians = pitch * Mth.DEG_TO_RAD;
        double horizontalLook = Math.sqrt(look.x * look.x + look.z * look.z);
        double horizontalSpeed = velocity.horizontalDistance();
        double cosSquared = Mth.square(Math.cos(pitchRadians));
        velocity = velocity.add(0, gravity * (-1 + cosSquared * 0.75), 0);
        if (velocity.y < 0 && horizontalLook > 0) {
            double lift = velocity.y * -0.1 * cosSquared;
            velocity = velocity.add(look.x * lift / horizontalLook, lift, look.z * lift / horizontalLook);
        }
        if (pitchRadians < 0 && horizontalLook > 0) {
            double climb = horizontalSpeed * -Mth.sin(pitchRadians) * 0.04;
            velocity = velocity.add(-look.x * climb / horizontalLook, climb * 3.2, -look.z * climb / horizontalLook);
        }
        if (horizontalLook > 0) {
            velocity = velocity.add((look.x / horizontalLook * horizontalSpeed - velocity.x) * 0.1, 0,
                    (look.z / horizontalLook * horizontalSpeed - velocity.z) * 0.1);
        }
        return velocity.multiply(0.99F, 0.98F, 0.99F);
    }
}
