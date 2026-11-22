package cc.swelink.utils.combat;

import cc.swelink.Paradox;
import cc.swelink.mixin.LivingEntityFlagsAccessor;
import cc.swelink.systems.modules.combat.KnockbackDelayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class ShieldBlockUtils {
    private static final double ANGLE_MARGIN = Math.toRadians(25);

    private ShieldBlockUtils() {
    }

    public static boolean wouldBlockAttack(Minecraft client, Player target) {
        var attacker = client.player;
        if (attacker == null || client.level == null || target == attacker || target.level() != client.level
                || !target.isAlive() || target.isSpectator() || !ShieldStates.INSTANCE.isUsingItem(target)) {
            return false;
        }
        var shield = ShieldStates.INSTANCE.itemInHand(target, ShieldStates.INSTANCE.usedHand(target));
        if (!shield.is(Items.SHIELD)) {
            return false;
        }
        var blocking = shield.get(DataComponents.BLOCKS_ATTACKS);
        if (blocking == null) {
            return false;
        }
        var source = attacker.damageSources().playerAttack(attacker);
        if (blocking.bypassedBy().map(types -> types.contains(source.typeHolder())).orElse(false)) {
            return false;
        }
        var serverPosition = Paradox.MODULES.get(KnockbackDelayModule.class).serverPosition(target.getId());
        double angle = Math.min(Math.min(horizontalAngle(target.position(), target.getYHeadRot(), attacker.position()),
                horizontalAngle(latestPosition(target), latestHeadYaw(target), attacker.position())),
                horizontalAngle(serverPosition != null ? serverPosition : latestPosition(target),
                        ShieldStates.INSTANCE.headYaw(target), attacker.position()));
        float damage = (float) Math.max(1, attacker.getAttributeValue(Attributes.ATTACK_DAMAGE));
        return blocking.resolveBlockedDamage(source, damage, Math.max(0, angle - ANGLE_MARGIN)) > 0;
    }

    private static Vec3 latestPosition(Player target) {
        var interpolation = target.getInterpolation();
        if (interpolation != null && interpolation.hasActiveInterpolation() && interpolation.target() != null) {
            return interpolation.target().position();
        }
        return target.position();
    }

    public static float latestHeadYaw(Player target) {
        var accessor = (LivingEntityFlagsAccessor) target;
        return accessor.paradox$lerpHeadSteps() > 0 ? (float) accessor.paradox$lerpYHeadRot() : target.getYHeadRot();
    }

    public static double horizontalAngle(Vec3 defender, float headYaw, Vec3 attacker) {
        var difference = attacker.subtract(defender);
        var towardAttacker = new Vec3(difference.x, 0, difference.z).normalize();
        var facing = Vec3.directionFromRotation(0, headYaw);
        return Math.acos(Math.clamp(towardAttacker.dot(facing), -1, 1));
    }
}
