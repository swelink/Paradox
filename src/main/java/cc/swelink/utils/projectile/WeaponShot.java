package cc.swelink.utils.projectile;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

public record WeaponShot(double speed, double gravity, boolean bow) {
    public static final double SPAWN_OFFSET = 0.1;
    private static final double ARROW_GRAVITY = 0.05;
    private static final double BOW_POWER = 3;
    private static final double CROSSBOW_ARROW_POWER = 3.15;
    private static final double CROSSBOW_FIREWORK_POWER = 1.6;

    public static WeaponShot ready(LocalPlayer player) {
        if (player.isUsingItem()) {
            if (!(player.getUseItem().getItem() instanceof BowItem)) return null;
            double power = BowItem.getPowerForTime(player.getTicksUsingItem() + 1);
            return new WeaponShot(power * BOW_POWER, ARROW_GRAVITY, true);
        }
        for (var hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof CrossbowItem) || !CrossbowItem.isCharged(stack)) continue;
            boolean firework = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)
                    .contains(Items.FIREWORK_ROCKET);
            return firework ? new WeaponShot(CROSSBOW_FIREWORK_POWER, 0, false)
                    : new WeaponShot(CROSSBOW_ARROW_POWER, ARROW_GRAVITY, false);
        }
        return null;
    }

    public static WeaponShot preview(LocalPlayer player) {
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof CrossbowItem) {
            return new WeaponShot(CROSSBOW_ARROW_POWER, ARROW_GRAVITY, false);
        }
        return ready(player);
    }

    public static EntityType<?> projectile(LocalPlayer player) {
        var ammo = ammo(player);
        if (ammo.is(Items.SPECTRAL_ARROW)) return EntityTypes.SPECTRAL_ARROW;
        return ammo.is(Items.FIREWORK_ROCKET) ? EntityTypes.FIREWORK_ROCKET : EntityTypes.ARROW;
    }

    public static ItemStack ammo(LocalPlayer player) {
        if (player.isUsingItem()) return player.getProjectile(player.getUseItem());
        for (var hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof CrossbowItem) || !CrossbowItem.isCharged(stack)) continue;
            return stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)
                    .itemCopies().findFirst().orElse(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    public static Vec3 motion(Entity entity) {
        return entity.position().subtract(entity.xOld, entity.yOld, entity.zOld);
    }

    public Vec3 inherited(LocalPlayer player) {
        if (!bow) return Vec3.ZERO;
        var motion = motion(player);
        return player.onGround() ? motion.multiply(1, 0, 1) : motion;
    }
}
