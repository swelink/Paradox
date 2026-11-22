package cc.swelink.utils.rotation;

import cc.swelink.Paradox;
import cc.swelink.mixin.ProjectileUtilInvoker;
import cc.swelink.systems.modules.movement.OmnisprintModule;
import cc.swelink.systems.modules.movement.speed.SpeedModule;
import cc.swelink.systems.modules.player.SpinbotModule;
import cc.swelink.utils.combat.Targets;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class RotationRaycast {
    public static Rotation interactionRotation(Minecraft client, float tickDelta) {
        boolean local = Paradox.MODULES.get(SpinbotModule.class).isEnabled() && !Paradox.ROTATIONS.hasHigherPriority(-1)
                || Paradox.MODULES.get(OmnisprintModule.class).usesRotations(client) && !Paradox.ROTATIONS.hasHigherPriority(-2)
                || Paradox.MODULES.get(SpeedModule.class).usesLocalTarget();
        var rotation = local ? null : Paradox.ROTATIONS.packetRotation();
        return rotation != null ? rotation
                : new Rotation(client.player.getViewYRot(tickDelta), client.player.getViewXRot(tickDelta));
    }

    public static HitResult traceHeldItem(Rotation rotation, float tickDelta) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.level == null) {
            return null;
        }
        var range = player.getActiveItem().get(DataComponents.ATTACK_RANGE);
        if (range != null) {
            Vec3 eyes = player.getEyePosition();
            Vec3 direction = rotation.direction();
            Vec3 start = eyes.add(direction.scale(range.effectiveMinRange(player)));
            double forwardMovement = Math.max(0, player.getKnownMovement().dot(direction));
            Vec3 end = eyes.add(direction.scale(range.effectiveMaxRange(player) + forwardMovement));
            var hits = ProjectileUtilInvoker.paradox$traceAttack(player, eyes, start, Targets::pickable,
                    end, range.hitboxMargin(), ClipContext.Block.OUTLINE);
            if (hits.left().isPresent()) {
                BlockHitResult block = hits.left().get();
                if (player.getEyePosition(tickDelta).distanceToSqr(block.getLocation())
                        <= player.blockInteractionRange() * player.blockInteractionRange()) {
                    return block;
                }
            } else {
                EntityHitResult closest = null;
                double closestDistance = Double.POSITIVE_INFINITY;
                Vec3 interpolatedEyes = player.getEyePosition(tickDelta);
                for (EntityHitResult hit : hits.right().orElseThrow()) {
                    double distance = interpolatedEyes.distanceToSqr(hit.getLocation());
                    if (distance < closestDistance) {
                        closest = hit;
                        closestDistance = distance;
                    }
                }
                if (closest != null) {
                    return closest;
                }
            }
        }
        return trace(rotation, player.blockInteractionRange(), player.entityInteractionRange(),
                ClipContext.Fluid.NONE, Targets::pickable);
    }

    public static HitResult trace(
            Rotation rotation,
            double blockRange,
            double entityRange,
            ClipContext.Fluid fluids,
            Predicate<Entity> entityPredicate
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }

        BlockHitResult blockHit = blocks(rotation, Math.max(blockRange, entityRange), fluids);
        EntityHitResult entityHit = entities(rotation, entityRange, entityPredicate);
        Vec3 eyes = minecraft.player.getEyePosition();
        if (entityHit != null && (blockHit == null || blockHit.getType() == HitResult.Type.MISS
                || eyes.distanceToSqr(entityHit.getLocation()) < eyes.distanceToSqr(blockHit.getLocation()))) {
            return entityHit;
        }
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
                && eyes.distanceToSqr(blockHit.getLocation()) > blockRange * blockRange) {
            return BlockHitResult.miss(blockHit.getLocation(), blockHit.getDirection(), blockHit.getBlockPos());
        }
        return blockHit;
    }

    public static BlockHitResult blocks(Rotation rotation, double range, ClipContext.Fluid fluids) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        Vec3 eyes = minecraft.player.getEyePosition();
        Vec3 end = eyes.add(rotation.direction().scale(range));
        return minecraft.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, fluids, minecraft.player));
    }

    public static BlockHitResult blocks(Rotation rotation, double range, ClipContext.Fluid fluids, BlockPos only) {
        Minecraft minecraft = Minecraft.getInstance();
        if (only == null || minecraft.player == null || minecraft.level == null) {
            return blocks(rotation, range, fluids);
        }
        Vec3 eyes = minecraft.player.getEyePosition();
        return block(eyes, eyes.add(rotation.direction().scale(range)), only, fluids);
    }

    public static BlockHitResult block(Vec3 from, Vec3 to, BlockPos pos) {
        return block(from, to, pos, ClipContext.Fluid.NONE);
    }

    public static BlockHitResult block(Vec3 from, Vec3 to, BlockPos pos, ClipContext.Fluid fluids) {
        Minecraft minecraft = Minecraft.getInstance();
        var state = minecraft.level.getBlockState(pos);
        var shape = state.getShape(minecraft.level, pos);
        BlockHitResult hit = shape.isEmpty() ? null : shape.clip(from, to, pos);
        var fluid = state.getFluidState();
        if (hit == null && fluids.canPick(fluid)) {
            hit = fluid.getShape(minecraft.level, pos).clip(from, to, pos);
        }
        if (hit != null) {
            return hit;
        }
        Vec3 delta = to.subtract(from);
        return BlockHitResult.miss(to, Direction.getApproximateNearest(delta.x, delta.y, delta.z), BlockPos.containing(to));
    }

    public static EntityHitResult entities(Rotation rotation, double range, Predicate<Entity> predicate) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        Vec3 eyes = minecraft.player.getEyePosition();
        Vec3 direction = rotation.direction();
        Vec3 end = eyes.add(direction.scale(range));
        return ProjectileUtil.getEntityHitResult(
                minecraft.player,
                eyes,
                end,
                minecraft.player.getBoundingBox().expandTowards(direction.scale(range)).inflate(1),
                predicate,
                range * range
        );
    }

    private RotationRaycast() {
    }
}
