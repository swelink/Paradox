package cc.swelink.utils.combat;

import cc.swelink.Paradox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.phys.Vec3;

public final class CriticalHitTiming {
    private static boolean stoppingSprint;

    private CriticalHitTiming() {
    }

    public static boolean canAttack(Minecraft client, boolean strict, boolean ignoreSprint) {
        var player = client.player;
        boolean critical = wouldCritical(player, true);
        if (strict) {
            return critical && (ignoreSprint || !player.isSprinting());
        }
        if (shouldWait(client)) {
            return false;
        }
        return ignoreSprint || !critical || !player.isSprinting();
    }

    public static boolean wouldCritical(LocalPlayer player, boolean ignoreSprint) {
        return !player.onGround() && player.fallDistance > 0
                && player.getAttackStrengthScale(0.5f) > 0.9f && allowsCritical(player)
                && (ignoreSprint || !player.isSprinting());
    }

    public static boolean shouldWait(Minecraft client) {
        LocalPlayer player = client.player;
        boolean grounded = player.onGround();
        boolean jumping = client.options.keyJump.isDown() || player.input.keyPresses.jump();
        if (grounded && jumping) {
            return true;
        }
        if (player.isFallFlying() || !allowsCritical(player) || grounded) {
            return false;
        }

        float delay = player.getCurrentItemAttackStrengthDelay();
        float nextCrit = Math.max(0, delay * (0.9f - player.getAttackStrengthScale(0)) - 0.5f);
        double motionY = player.getDeltaMovement().y;
        if (motionY <= 0) {
            if (player.fallDistance > 0 && player.getAttackStrengthScale(0.5f) > 0.9f) {
                return false;
            }
            int landing = landingTick(player, motionY, (int) (nextCrit + 1));
            return landing < 0 || landing >= (int) nextCrit;
        }

        float ticksToFall = (float) (motionY / Math.max(player.getGravity(), 1.0E-6));
        float ticksToCrit = Math.max(nextCrit, ticksToFall);
        int landing = landingTick(player, motionY, (int) (ticksToCrit + 5));
        return landing < 0 || landing >= (int) ticksToFall;
    }

    private static boolean allowsCritical(LocalPlayer player) {
        if (player.isInLiquid() || player.isPassenger() || player.onClimbable()
                || player.isNoGravity() || player.isHandsBusy() || player.getAbilities().flying) {
            return false;
        }
        if (player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(MobEffects.LEVITATION)
                || player.hasEffect(MobEffects.SLOW_FALLING)) {
            return false;
        }
        return player.level().getBlockStates(player.getBoundingBox())
                .noneMatch(state -> state.getBlock() instanceof WebBlock);
    }

    public static void stopSprint() {
        stoppingSprint = true;
    }

    public static boolean stoppingSprint() {
        return stoppingSprint;
    }

    public static void resetSprint() {
        stoppingSprint = false;
    }

    private static int landingTick(LocalPlayer player, double motionY, int ticks) {
        var level = player.level();
        var box = player.getBoundingBox();
        Vec3 motion = new Vec3(player.getDeltaMovement().x, motionY, player.getDeltaMovement().z);
        var input = player.input.getMoveVector();
        double yaw = Math.toRadians(Paradox.ROTATIONS.movementYaw(player.getYRot()));
        double speed = player.getSpeed() * 0.1 * 0.98;
        Vec3 acceleration = new Vec3((input.x * Math.cos(yaw) - input.y * Math.sin(yaw)) * speed,
                0, (input.y * Math.cos(yaw) + input.x * Math.sin(yaw)) * speed);
        int maxTicks = Math.min(Math.max(ticks, 0), 200);
        for (int tick = 0; tick < maxTicks; tick++) {
            Vec3 intendedMovement = motion.add(acceleration);
            var colliders = level.getEntityCollisions(player, box.expandTowards(intendedMovement));
            Vec3 resolvedMovement = Entity.collideBoundingBox(player, intendedMovement, box, level, colliders);
            if (intendedMovement.y < 0 && resolvedMovement.y != intendedMovement.y) {
                return tick;
            }
            box = box.move(resolvedMovement);
            motion = new Vec3(
                    (resolvedMovement.x == intendedMovement.x ? intendedMovement.x : 0) * LivingEntity.BASE_HORIZONTAL_AIR_DRAG,
                    ((resolvedMovement.y == intendedMovement.y ? intendedMovement.y : 0) - player.getGravity())
                            * LivingEntity.BASE_VERTICAL_AIR_DRAG,
                    (resolvedMovement.z == intendedMovement.z ? intendedMovement.z : 0) * LivingEntity.BASE_HORIZONTAL_AIR_DRAG);
        }
        return -1;
    }
}
