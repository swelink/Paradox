package cc.swelink.systems.modules.combat;

import cc.swelink.events.AttackEvent;
import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.events.PostAttackEvent;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.Targets;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.phys.Vec3;

public final class CriticalsModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Legit", "Legit", "Custom"));
    private final IntSetting radius = addSetting(new IntSetting("Radius", 3, 1, 6));
    private LocalPlayer spoofedPlayer;
    private Vec3 spoofOrigin;
    private boolean restoreSprint;

    public CriticalsModule() {
        super("Criticals", "Criticals for the bypass", Category.COMBAT);
        radius.visibleWhen(() -> mode.get().equals("Legit"));
    }

    public Input prepareInput(Minecraft client, Input input) {
        var player = client.player;
        if (!isEnabled() || !mode.get().equals("Legit") || !canCritical(client) || !hasNearbyPlayer(client)) {
            return input;
        }
        float delay = player.getCurrentItemAttackStrengthDelay();
        float remaining = Math.max(0, delay * (0.9f - player.getAttackStrengthScale(0)) - 0.5f);
        boolean jump = player.onGround() && !input.shift() && remaining < lastCriticalTick(player)
                && client.level.noCollision(player, player.getBoundingBox().move(0, 0.1, 0));
        return new Input(input.forward(), input.backward(), input.left(), input.right(),
                input.jump() || jump, input.shift(), input.sprint());
    }

    public void beforeAttack(Minecraft client, int entityId) {
        var player = client.player;
        if (!isEnabled() || !mode.get().equals("Custom") || !canCritical(client)
                || player.getAttackStrengthScale(0.5f) <= 0.9f || spoofedPlayer != null
                || !(client.level.getEntity(entityId) instanceof LivingEntity target) || target == player
                || !target.isAlive() || !client.level.noCollision(player, player.getBoundingBox().move(0, 0.0625, 0))) {
            return;
        }
        spoofedPlayer = player;
        spoofOrigin = player.position();
        restoreSprint = player.isSprinting();
        if (restoreSprint) {
            player.connection.send(new ServerboundPlayerCommandPacket(player,
                    ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }
        player.connection.send(new ServerboundMovePlayerPacket.Pos(
                spoofOrigin.x, spoofOrigin.y + 0.0625, spoofOrigin.z, false, player.horizontalCollision));
        player.connection.send(new ServerboundMovePlayerPacket.Pos(
                spoofOrigin.x, spoofOrigin.y + 0.001, spoofOrigin.z, false, player.horizontalCollision));
    }

    public void afterAttack(Minecraft client) {
        if (spoofedPlayer == null) {
            return;
        }
        if (client.player == spoofedPlayer && client.level == spoofedPlayer.level()) {
            spoofedPlayer.connection.send(new ServerboundMovePlayerPacket.Pos(
                    spoofOrigin, spoofedPlayer.onGround(), spoofedPlayer.horizontalCollision));
            if (restoreSprint && spoofedPlayer.isSprinting()) {
                spoofedPlayer.connection.send(new ServerboundPlayerCommandPacket(spoofedPlayer,
                        ServerboundPlayerCommandPacket.Action.START_SPRINTING));
            }
        }
        spoofedPlayer = null;
        spoofOrigin = null;
        restoreSprint = false;
    }

    @Override
    protected void onDisable() {
        afterAttack(Minecraft.getInstance());
    }

    private boolean hasNearbyPlayer(Minecraft client) {
        for (var target : client.level.players()) {
            if (isNearbyPlayer(client.player, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearbyPlayer(LocalPlayer player, Player target) {
        return Targets.attackable(Minecraft.getInstance(), target) && target.isPickable()
                && player.hasLineOfSight(target) && player.getEyePosition().distanceToSqr(
                        RotationUtils.nearestPoint(player.getEyePosition(), target.getBoundingBox())) <= radius.get() * radius.get();
    }

    private static int lastCriticalTick(LocalPlayer player) {
        var level = player.level();
        float jumpFactor = level.getBlockState(player.blockPosition()).getBlock().getJumpFactor();
        if (jumpFactor == 1) {
            jumpFactor = level.getBlockState(player.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();
        }
        double motionY = player.getAttributeValue(Attributes.JUMP_STRENGTH) * jumpFactor + player.getJumpBoostPower();
        double gravity = Math.max(player.getGravity(), 1.0e-6);
        var box = player.getBoundingBox();
        var motion = new Vec3(player.getDeltaMovement().x, motionY, player.getDeltaMovement().z);
        int lastFallingTick = 0;
        for (int tick = 1; tick <= 40; tick++) {
            var colliders = level.getEntityCollisions(player, box.expandTowards(motion));
            var movement = Entity.collideBoundingBox(player, motion, box, level, colliders);
            if (motion.y < 0) {
                if (movement.y != motion.y) {
                    return lastFallingTick;
                }
                lastFallingTick = tick;
            }
            box = box.move(movement);
            motion = new Vec3(
                    (movement.x == motion.x ? motion.x : 0) * LivingEntity.BASE_HORIZONTAL_AIR_DRAG,
                    ((movement.y == motion.y ? motion.y : 0) - gravity) * LivingEntity.BASE_VERTICAL_AIR_DRAG,
                    (movement.z == motion.z ? motion.z : 0) * LivingEntity.BASE_HORIZONTAL_AIR_DRAG);
        }
        return lastFallingTick;
    }

    private static boolean canCritical(Minecraft client) {
        var player = client.player;
        return Gameplay.canAct(client) && client.gameMode != null
                && !player.isUsingItem() && !player.isHandsBusy()
                && !player.isInLiquid() && !player.isPassenger() && !player.onClimbable()
                && !player.isNoGravity() && !player.isFallFlying() && !player.getAbilities().flying
                && !player.hasEffect(MobEffects.BLINDNESS) && !player.hasEffect(MobEffects.LEVITATION)
                && !player.hasEffect(MobEffects.SLOW_FALLING)
                && client.level.getBlockStates(player.getBoundingBox())
                        .noneMatch(state -> state.getBlock() instanceof WebBlock);
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 70)
    private void onAttack(AttackEvent event) {
        beforeAttack(event.client(), event.entityId());
    }

    @EventHandler(priority = 10)
    private void onPostAttack(PostAttackEvent event) {
        afterAttack(event.client());
    }

    @EventHandler(priority = 50)
    private void onInput(InputEvent event) {
        event.input(prepareInput(event.client(), event.input()));
    }
}
