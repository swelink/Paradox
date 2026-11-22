package cc.swelink.utils.projectile;

import cc.swelink.utils.rotation.Rotation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public record HeldThrow(double speed, double gravity, boolean arrow, float pitchOffset, boolean inherit, ItemStack icon) {
    public static HeldThrow find(LocalPlayer player) {
        var shot = WeaponShot.preview(player);
        if (shot != null) {
            var ammo = WeaponShot.ammo(player);
            return new HeldThrow(shot.speed(), shot.gravity(), true, 0, shot.bow(),
                    ammo.isEmpty() ? new ItemStack(Items.ARROW) : ammo.copyWithCount(1));
        }
        if (player.isUsingItem()) {
            var using = player.getUseItem();
            if (using.getItem() instanceof TridentItem && EnchantmentHelper.getTridentSpinAttackStrength(using, player) <= 0) {
                return new HeldThrow(TridentItem.PROJECTILE_SHOOT_POWER, 0.05, true, 0, true, using.copyWithCount(1));
            }
            return null;
        }
        for (var hand : InteractionHand.values()) {
            var found = of(player.getItemInHand(hand));
            if (found != null) return found;
        }
        return null;
    }

    private static HeldThrow of(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderpearlItem) {
            return new HeldThrow(1.5, 0.03, false, 0, true, stack.copyWithCount(1));
        }
        if (item instanceof ThrowablePotionItem) {
            return new HeldThrow(ThrowablePotionItem.PROJECTILE_SHOOT_POWER, 0.05, false, -20, true, stack.copyWithCount(1));
        }
        if (item instanceof ExperienceBottleItem) {
            return new HeldThrow(0.7, 0.07, false, -20, true, stack.copyWithCount(1));
        }
        return null;
    }

    public Vec3 velocity(LocalPlayer player, Rotation rotation) {
        float pitch = rotation.pitch() * Mth.DEG_TO_RAD;
        float yaw = rotation.yaw() * Mth.DEG_TO_RAD;
        var direction = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin((rotation.pitch() + pitchOffset) * Mth.DEG_TO_RAD),
                Mth.cos(yaw) * Mth.cos(pitch)).normalize();
        var velocity = direction.scale(speed);
        return inherit ? velocity.add(new WeaponShot(0, gravity, true).inherited(player)) : velocity;
    }
}
