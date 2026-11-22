package cc.swelink.systems.modules.render;

import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.FakePlayerEntity;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public final class FakePlayerModule extends Module {
    private static final int ID = -1337420;
    private FakePlayerEntity fake;
    private ClientLevel level;

    public FakePlayerModule() {
        super("FakePlayer", "Spawns a clone to test on", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.level == null) {
            setEnabled(false);
            return;
        }
        level = client.level;
        fake = new FakePlayerEntity(level, new GameProfile(UUID.randomUUID(), player.getGameProfile().name()));
        fake.setId(ID);
        fake.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        fake.setYHeadRot(player.getYHeadRot());
        fake.yBodyRot = player.yBodyRot;
        fake.getInventory().replaceWith(player.getInventory());
        for (var slot : EquipmentSlot.values()) fake.setItemSlot(slot, player.getItemBySlot(slot).copy());
        fake.setHealth(fake.getMaxHealth());
        level.addEntity(fake);
    }

    @Override
    protected void onDisable() {
        if (fake != null && level != null && level == Minecraft.getInstance().level) {
            level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
        }
        fake = null;
        level = null;
    }

    @Override
    public void tick(Minecraft client) {
        if (fake != null && (client.level != level || fake.isRemoved())) {
            fake = null;
            setEnabled(false);
        }
    }

    public boolean isFake(int entityId) {
        return isEnabled() && fake != null && entityId == ID;
    }

    public void hit(Minecraft client) {
        var player = client.player;
        if (fake == null || player == null) return;
        if (fake.getInvulnerableTime() > 10) {
            sound(SoundEvents.PLAYER_ATTACK_NODAMAGE);
            return;
        }
        float cooldown = player.getAttackStrengthScale(0.5f);
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2 + cooldown * cooldown * 0.8));
        boolean strong = cooldown > 0.9f;
        boolean critical = strong && player.fallDistance > 0 && !player.onGround() && !player.onClimbable()
                && !player.isInWater() && !player.isPassenger();
        boolean knockback = strong && player.isSprinting();
        boolean sweep = strong && !critical && !knockback && player.onGround()
                && player.getMainHandItem().is(ItemTags.SWORDS);
        if (critical) {
            damage *= 1.5f;
            client.particleEngine.createTrackingEmitter(fake, ParticleTypes.CRIT);
        }
        if (knockback) sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK);
        if (sweep) {
            sound(SoundEvents.PLAYER_ATTACK_SWEEP);
            level.addParticle(ParticleTypes.SWEEP_ATTACK, fake.getX(), fake.getY(0.5), fake.getZ(), 0, 0, 0);
        }
        sound(critical ? SoundEvents.PLAYER_ATTACK_CRIT : strong ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK);
        sound(SoundEvents.PLAYER_HURT);
        fake.setInvulnerableTime(20);
        fake.handleDamageEvent(level.damageSources().playerAttack(player));
        float health = fake.getHealth() - damage;
        if (health <= 0) {
            client.particleEngine.createTrackingEmitter(fake, ParticleTypes.TOTEM_OF_UNDYING, 30);
            sound(SoundEvents.TOTEM_USE);
            health = fake.getMaxHealth();
        }
        fake.setHealth(health);
    }

    private void sound(SoundEvent sound) {
        level.playLocalSound(fake.getX(), fake.getY(), fake.getZ(), sound, SoundSource.PLAYERS, 1, 1, false);
    }
}
