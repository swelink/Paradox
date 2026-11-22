package cc.swelink.utils.combat;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.AntiBotModule;
import cc.swelink.systems.modules.combat.StickyTargetModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import cc.swelink.utils.rotation.RotationUtils;

public final class AttackUtils {
    private AttackUtils() {
    }

    public static boolean attack(Minecraft client, Entity target) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null || player.isSpectator()
                || target == null || target == player || !target.isAlive() || target.level() != client.level
                || target instanceof ItemEntity || target instanceof ExperienceOrb
                || target instanceof AbstractArrow arrow && !arrow.isAttackable()
                || Paradox.MODULES.get(AntiBotModule.class).isBot(target)
                || !Paradox.MODULES.get(StickyTargetModule.class).allows(client, target)) {
            return false;
        }

        if (player.getMainHandItem().has(DataComponents.PIERCING_WEAPON)) {
            return piercingAttack(client);
        }

        var animation = player.getMainHandItem().getAttackAnimation();
        client.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND, animation, false);
        player.connection.send(ServerboundPunchPacket.INSTANCE);
        return true;
    }

    public static boolean piercingAttack(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null || client.player.isSpectator()) {
            return false;
        }
        var stack = client.player.getMainHandItem();
        var piercing = stack.get(DataComponents.PIERCING_WEAPON);
        if (piercing == null) {
            return false;
        }
        client.gameMode.piercingAttack(stack.getAttackAnimation(), piercing);
        return true;
    }

    public static boolean isInRange(Player player, EntityHitResult hit) {
        var range = player.getMainHandItem().get(DataComponents.ATTACK_RANGE);
        return range != null ? range.isInRange(player, hit.getLocation())
                : player.isWithinEntityInteractionRange(hit.getEntity(), 0);
    }

    public static Vec3 aimPoint(Player target, Vec3 eyes, boolean head, double inset) {
        var hitbox = target.getBoundingBox();
        if (head || hitbox.contains(eyes)) return target.getEyePosition();
        return RotationUtils.nearestPoint(eyes, hitbox.deflate(hitbox.getXsize() * inset,
                hitbox.getYsize() * inset, hitbox.getZsize() * inset));
    }
}
