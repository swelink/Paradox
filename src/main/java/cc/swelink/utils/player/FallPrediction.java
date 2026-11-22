package cc.swelink.utils.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

public final class FallPrediction {
    public enum Kind {
        GROUND, LIQUID, VOID, UNKNOWN
    }

    public record Result(Kind kind, BlockPos position, double top, double fall) {
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final double HORIZONTAL_DRAG = 0.91;
    private static final double VERTICAL_DRAG = 0.98;
    private static final double SLOW_FALLING_GRAVITY = 0.01;
    private static final double EDGE_INSET = 0.001;
    private static final int MAX_PROTECTION = 20;
    private static final float PROTECTION_DIVISOR = 25;
    private static final int FEATHER_FALLING_POINTS = 3;
    private static final float RESISTANCE_PER_LEVEL = 0.2f;

    private FallPrediction() {
    }

    public static boolean airborne(LocalPlayer player) {
        return !player.onGround() && !player.isInWater() && !player.isInLava() && !player.isPassenger()
                && !player.isFallFlying() && !player.onClimbable() && !player.getAbilities().flying
                && !player.getAbilities().invulnerable && !player.hasEffect(MobEffects.LEVITATION);
    }

    public static Result predict(ClientLevel level, LocalPlayer player, int maxTicks) {
        var position = player.position();
        var velocity = player.getDeltaMovement();
        var slowFalling = player.getEffect(MobEffects.SLOW_FALLING);
        double half = player.getBbWidth() / 2 - EDGE_INSET;
        double[][] offsets = {{0, 0}, {-half, -half}, {-half, half}, {half, -half}, {half, half}};
        double fall = player.fallDistance;
        var cursor = new BlockPos.MutableBlockPos();
        for (int tick = 1; tick <= maxTicks; tick++) {
            boolean slowed = slowFalling != null && (slowFalling.isInfiniteDuration() || slowFalling.getDuration() >= tick);
            if (slowed) fall = 0;
            var next = position.add(velocity);
            for (int y = Mth.floor(position.y); y >= Mth.floor(next.y); y--) {
                if (y < level.getMinY()) return new Result(Kind.VOID, null, 0, fall);
                for (double[] offset : offsets) {
                    cursor.set(Mth.floor(next.x + offset[0]), y, Mth.floor(next.z + offset[1]));
                    var state = level.getBlockState(cursor);
                    if (!state.getFluidState().isEmpty()) {
                        return new Result(Kind.LIQUID, cursor.immutable(), y + 1, fall);
                    }
                    var shape = state.getCollisionShape(level, cursor);
                    if (shape.isEmpty()) continue;
                    double top = y + shape.max(Direction.Axis.Y);
                    if (top <= position.y && top >= next.y) {
                        return new Result(Kind.GROUND, cursor.immutable(), top, fall + position.y - top);
                    }
                }
            }
            fall += Math.max(position.y - next.y, 0);
            position = next;
            double gravity = slowed ? Math.min(player.getGravity(), SLOW_FALLING_GRAVITY) : player.getGravity();
            velocity = new Vec3(velocity.x * HORIZONTAL_DRAG, (velocity.y - gravity) * VERTICAL_DRAG,
                    velocity.z * HORIZONTAL_DRAG);
        }
        return new Result(Kind.UNKNOWN, null, 0, fall);
    }

    public static Vec3 positionAfter(LocalPlayer player, int ticks) {
        var position = player.position();
        var velocity = player.getDeltaMovement();
        double gravity = player.getGravity();
        for (int tick = 0; tick < ticks; tick++) {
            position = position.add(velocity);
            velocity = new Vec3(velocity.x * HORIZONTAL_DRAG, (velocity.y - gravity) * VERTICAL_DRAG,
                    velocity.z * HORIZONTAL_DRAG);
        }
        return position;
    }

    public static boolean exceeds(Minecraft client, double fall, float maxDamage) {
        var player = client.player;
        float damage = damage(client, fall);
        return damage > maxDamage || damage > 0 && damage >= player.getHealth() + player.getAbsorptionAmount();
    }

    public static float damage(Minecraft client, double fall) {
        var player = client.player;
        double raw = (fall - player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE))
                * player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        if (raw <= 0) return 0;
        int protection = 0;
        for (var slot : ARMOR_SLOTS) {
            var armor = player.getItemBySlot(slot);
            protection += enchantment(client, armor, Enchantments.PROTECTION)
                    + enchantment(client, armor, Enchantments.FEATHER_FALLING) * FEATHER_FALLING_POINTS;
        }
        float damage = (float) Math.ceil(raw) * (1 - Math.min(protection, MAX_PROTECTION) / PROTECTION_DIVISOR);
        var resistance = player.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            damage *= Math.max(1 - RESISTANCE_PER_LEVEL * (resistance.getAmplifier() + 1), 0);
        }
        return damage;
    }

    private static int enchantment(Minecraft client, ItemStack stack, ResourceKey<Enchantment> key) {
        var enchantment = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
        return enchantment.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
    }
}
