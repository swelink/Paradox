package cc.swelink.utils.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class MeleeDamageEstimate {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private MeleeDamageEstimate() {
    }

    public static float healthDamage(Minecraft client, Player target) {
        return Math.max(0, dealtDamage(client, target) - target.getAbsorptionAmount());
    }

    public static float dealtDamage(Minecraft client, Player target) {
        var attacker = client.player;
        if (attacker == null || target == attacker || attacker.isSpectator() || !target.isAlive()
                || target.isSpectator() || target.getAbilities().invulnerable || !target.isAttackable()) return 0;
        ItemStack weapon = attacker.getMainHandItem();
        float charge = attacker.getAttackStrengthScale(0.5f);
        float base = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = base * (0.2f + charge * charge * 0.8f);
        var source = weapon.getDamageSource(attacker);
        damage += weapon.getItem().getAttackDamageBonus(target, damage, source);
        if (charge > 0.9f && attacker.fallDistance > 0 && !attacker.onGround() && !attacker.onClimbable()
                && !attacker.isInWater() && !attacker.isMobilityRestricted() && !attacker.isPassenger()
                && !attacker.isSprinting()) damage *= 1.5f;
        int sharpness = enchantment(weapon, Enchantments.SHARPNESS, client);
        if (sharpness > 0) damage += (0.5f * sharpness + 0.5f) * charge;
        if (target.isBlocking()) {
            ItemStack shield = target.getItemBlockingWith();
            var blocking = shield.get(DataComponents.BLOCKS_ATTACKS);
            if (blocking != null && !blocking.bypassedBy().map(types -> types.contains(source.typeHolder())).orElse(false)) {
                double angle = ShieldBlockUtils.horizontalAngle(target.position(), target.getYHeadRot(), attacker.position());
                damage -= blocking.resolveBlockedDamage(source, damage, angle);
            }
        }
        int protection = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            protection += enchantment(target.getItemBySlot(slot), Enchantments.PROTECTION, client);
        }
        var resistance = target.getEffect(MobEffects.RESISTANCE);
        int resistanceLevel = resistance == null ? 0 : resistance.getAmplifier() + 1;
        return reduce(damage, target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                protection, resistanceLevel, 0, enchantment(weapon, Enchantments.BREACH, client));
    }

    private static int enchantment(ItemStack stack, ResourceKey<Enchantment> key, Minecraft client) {
        var enchantment = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
        return enchantment.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
    }

    static float reduce(float damage, float armor, float toughness, int protection, int resistance,
                        float absorption, int breach) {
        if (!Float.isFinite(damage) || damage <= 0) return 0;
        float effectiveArmor = Math.clamp(armor - damage / (2 + toughness / 4), armor * 0.2f, 20);
        float armorReduction = Math.clamp(effectiveArmor / 25 - breach * 0.15f, 0, 1);
        damage *= 1 - armorReduction;
        damage *= Math.max(0, 1 - resistance * 0.2f);
        damage *= 1 - Math.clamp(protection, 0, 20) / 25f;
        return Math.max(0, damage - absorption);
    }
}
