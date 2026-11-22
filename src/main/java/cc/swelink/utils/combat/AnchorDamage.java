package cc.swelink.utils.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AnchorDamage {
    private static final float RADIUS = 5;
    private static final float CRYSTAL_RADIUS = 6;
    private static final float CART_RADIUS = 4;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private AnchorDamage() {
    }

    public static boolean harmless(Player player) {
        return player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable
                || player.level().getDifficulty() == Difficulty.PEACEFUL;
    }

    public static float blastDamage(Player player, BlockPos anchor, BlockPos shield) {
        return blastDamage(player, Vec3.atCenterOf(anchor), RADIUS, anchor, shield);
    }

    public static float crystalDamage(Player player, Vec3 crystal) {
        return blastDamage(player, crystal, CRYSTAL_RADIUS, null, null);
    }

    public static float cartHealthDamage(Minecraft client, Player player, Vec3 cart, BlockPos shield, VoxelShape shape) {
        return reduced(client, player, blastDamage(player, cart, CART_RADIUS, null, shield, shape));
    }

    private static float blastDamage(Player player, Vec3 center, float radius, BlockPos anchor, BlockPos shield) {
        return blastDamage(player, center, radius, anchor, shield, Shapes.block());
    }

    private static float blastDamage(Player player, Vec3 center, float radius, BlockPos anchor, BlockPos shield,
                                     VoxelShape shape) {
        return blastDamage(player, center, radius, anchor, shield, shape, Vec3.ZERO);
    }

    private static float blastDamage(Player player, Vec3 center, float radius, BlockPos anchor, BlockPos shield,
                                     VoxelShape shape, Vec3 offset) {
        Level level = player.level();
        double distance = Math.sqrt(player.position().add(offset).distanceToSqr(center)) / (radius * 2);
        if (distance > 1) return 0;
        double impact = (1 - distance) * exposure(level, center, player, anchor, shield, shape, offset);
        float damage = (float) ((impact * impact + impact) / 2 * 7 * (radius * 2) + 1);
        Difficulty difficulty = level.getDifficulty();
        if (difficulty == Difficulty.EASY) damage = Math.min(damage / 2 + 1, damage);
        else if (difficulty == Difficulty.HARD) damage = damage * 3 / 2;
        return damage;
    }

    public static float healthDamage(Minecraft client, Player player, BlockPos anchor, BlockPos shield) {
        return reduced(client, player, blastDamage(player, anchor, shield));
    }

    public static float crystalHealthDamage(Minecraft client, Player player, Vec3 crystal) {
        return reduced(client, player, crystalDamage(player, crystal));
    }

    public static float crystalHealthDamage(Minecraft client, Player player, Vec3 crystal, BlockPos shield) {
        return reduced(client, player, blastDamage(player, crystal, CRYSTAL_RADIUS, null, shield));
    }

    public static float crystalDamage(Player player, Vec3 crystal, Vec3 offset) {
        return blastDamage(player, crystal, CRYSTAL_RADIUS, null, null, Shapes.block(), offset);
    }

    public static float crystalHealthDamage(Minecraft client, Player player, Vec3 crystal, BlockPos shield, Vec3 offset) {
        return reduced(client, player, blastDamage(player, crystal, CRYSTAL_RADIUS, null, shield, Shapes.block(), offset));
    }

    private static float reduced(Minecraft client, Player player, float damage) {
        if (damage <= 0) return 0;
        int protection = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = player.getItemBySlot(slot);
            protection += enchantment(client, armor, Enchantments.PROTECTION)
                    + enchantment(client, armor, Enchantments.BLAST_PROTECTION) * 2;
        }
        var resistance = player.getEffect(MobEffects.RESISTANCE);
        int resistanceLevel = resistance == null ? 0 : resistance.getAmplifier() + 1;
        return MeleeDamageEstimate.reduce(damage, player.getArmorValue(),
                (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), protection, resistanceLevel,
                player.getAbsorptionAmount(), 0);
    }

    private static float exposure(Level level, Vec3 center, Player player, BlockPos anchor, BlockPos shield,
                                  VoxelShape shape, Vec3 offset) {
        var box = player.getBoundingBox().move(offset);
        double stepX = 1 / ((box.maxX - box.minX) * 2 + 1);
        double stepY = 1 / ((box.maxY - box.minY) * 2 + 1);
        double stepZ = 1 / ((box.maxZ - box.minZ) * 2 + 1);
        double offsetX = (1 - Math.floor(1 / stepX) * stepX) / 2;
        double offsetZ = (1 - Math.floor(1 / stepZ) * stepZ) / 2;
        if (stepX < 0 || stepY < 0 || stepZ < 0) return 0;
        var context = CollisionContext.of(player);
        int clear = 0;
        int total = 0;
        for (double x = 0; x <= 1; x += stepX) {
            for (double y = 0; y <= 1; y += stepY) {
                for (double z = 0; z <= 1; z += stepZ) {
                    Vec3 point = new Vec3(Mth.lerp(x, box.minX, box.maxX) + offsetX, Mth.lerp(y, box.minY, box.maxY),
                            Mth.lerp(z, box.minZ, box.maxZ) + offsetZ);
                    if (!blocked(level, point, center, anchor, shield, shape, context)) clear++;
                    total++;
                }
            }
        }
        return (float) clear / total;
    }

    private static boolean blocked(Level level, Vec3 from, Vec3 to, BlockPos anchor, BlockPos shield,
                                   VoxelShape shieldShape, CollisionContext context) {
        return BlockGetter.traverseBlocks(from, to, level, (world, pos) -> {
            if (pos.equals(anchor)) return null;
            if (pos.equals(shield)) return shieldShape.clip(from, to, pos) != null ? Boolean.TRUE : null;
            var shape = world.getBlockState(pos).getCollisionShape(world, pos, context);
            return shape.clip(from, to, pos) != null ? Boolean.TRUE : null;
        }, world -> Boolean.FALSE);
    }

    private static int enchantment(Minecraft client, ItemStack stack, ResourceKey<Enchantment> key) {
        var enchantment = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
        return enchantment.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
    }
}
