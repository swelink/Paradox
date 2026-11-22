package cc.swelink.utils.combat;

import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

public final class EnemyRaycast {
    private EnemyRaycast() {
    }

    public static EntityHitResult trace(Minecraft client, Entity target, Rotation rotation) {
        var player = client.player;
        if (player == null || target == null || target == player || !target.isAlive() || target.isSpectator()
                || !target.isPickable()) {
            return null;
        }
        var eyes = player.getEyePosition();
        var direction = rotation.direction();
        var range = player.getMainHandItem().get(DataComponents.ATTACK_RANGE);
        double minimum = range == null ? 0 : range.effectiveMinRange(player);
        double maximum = range == null ? player.entityInteractionRange()
                : range.effectiveMaxRange(player) + Math.max(0, player.getKnownMovement().dot(direction));
        double margin = range == null ? 0 : range.hitboxMargin();
        var start = eyes.add(direction.scale(minimum));
        var end = eyes.add(direction.scale(maximum));
        var hitbox = target.getBoundingBox();
        var box = hitbox.inflate(margin);
        var point = box.contains(start) ? start : box.clip(start, end).orElse(null);
        if (point == null) return null;
        var hit = new EntityHitResult(target, RotationUtils.nearestPoint(point, hitbox));
        return AttackUtils.isInRange(player, hit) ? hit : null;
    }
}
