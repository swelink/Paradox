package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.player.MultiActionsModule;
import cc.swelink.utils.combat.AttackUtils;
import cc.swelink.utils.combat.CriticalHitTiming;
import cc.swelink.utils.combat.EnemyRaycast;
import cc.swelink.utils.combat.TargetFinder;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationSettings;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class KillauraModule extends Module {
    private final ModeSetting critMode = addSetting(new ModeSetting("Crit mode", "Off", "Off", "Smart", "Strict"));
    private final PercentSetting swordCooldown = addSetting(new PercentSetting("Sword cooldown", 90));
    private final PercentSetting itemCooldown = addSetting(new PercentSetting("Item cooldown", 100));
    private final IntSetting range = addSetting(new IntSetting("Range", 3, 1, 6));
    private final IntSetting fov = addSetting(new IntSetting("FOV", 360, 10, 360));
    private final RotationSettings rotations = new RotationSettings(this::addSetting);
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final PercentSetting aimInset = addSetting(new PercentSetting("Aim inset", 25));
    private final ModeSetting aimMode = addSetting(new ModeSetting("Aim mode", "Multipoint", "Multipoint", "Head"));

    public KillauraModule() {
        super("KillAura", "Hits players around you", Category.COMBAT);
        aimInset.visibleWhen(() -> aimMode.get().equals("Multipoint"));
    }

    @Override
    protected void onDisable() {
        Paradox.ROTATIONS.clear();
        CriticalHitTiming.resetSprint();
    }

    @Override
    public void tick(Minecraft client) {
        if (Paradox.MODULES.get(AutoTrapModule.class).trapping(client)) return;
        var target = target(client);
        if (target == null) return;
        Paradox.TARGETS.report(target);
        var eyes = client.player.getEyePosition();
        rotations.request(client, RotationUtils.lookAt(eyes, aimPoint(target, eyes)), 1);
    }

    @Override
    public void tickInput(Minecraft client) {
        if (Paradox.MODULES.get(AutoTrapModule.class).trapping(client)) return;
        if (!Gameplay.canAct(client) || client.gameMode == null || client.player.isHandsBusy() || SlotSelection.isOwned()) {
            return;
        }
        var rotation = RotationRaycast.interactionRotation(client, 1);
        var enemy = raycast.target() ? target(client) : null;
        attack(client, enemy != null ? EnemyRaycast.trace(client, enemy, rotation) : client.hitResult);
    }

    public Player target(Minecraft client) {
        if (!isEnabled() || !Gameplay.canAct(client)) return null;
        var player = client.player;
        var eyes = player.getEyePosition();
        double maxDistance = range.get() * range.get();
        return TargetFinder.nearest(client, raycast.target(), target -> {
            if (target == player || TargetFinder.hitboxDistanceSqr(eyes, target) > maxDistance) return false;
            var point = aimPoint(target, eyes);
            return eyes.distanceToSqr(point) >= 1.0e-8 && Math.abs(RotationUtils.yawDifference(
                    player.getYRot(), RotationUtils.lookAt(eyes, point).yaw())) <= fov.get() / 2f;
        }, target -> TargetFinder.hitboxDistanceSqr(eyes, target));
    }

    private Vec3 aimPoint(Player target, Vec3 eyes) {
        boolean multipoint = aimMode.get().equals("Multipoint");
        return AttackUtils.aimPoint(target, eyes, aimMode.get().equals("Head"), multipoint ? aimInset.get() / 200.0 : 0);
    }

    private void attack(Minecraft client, HitResult targetHit) {
        var player = client.player;
        if (!(targetHit instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player target)
                || !TargetFinder.eligible(client, target, true) || !AttackUtils.isInRange(player, hit)) {
            return;
        }
        var multiActions = Paradox.MODULES.get(MultiActionsModule.class);
        if (player.isUsingItem() && !multiActions.isEnabled()) return;
        Paradox.TARGETS.report(target);

        int cooldown = player.getMainHandItem().is(ItemTags.SWORDS) ? swordCooldown.get() : itemCooldown.get();
        if (player.getAttackStrengthScale(0) < cooldown / 100f) return;

        if (!critMode.get().equals("Off")) {
            boolean strict = critMode.get().equals("Strict");
            if (!CriticalHitTiming.canAttack(client, strict, true)) return;
            if (CriticalHitTiming.wouldCritical(player, true)) CriticalHitTiming.stopSprint();
            if (!CriticalHitTiming.canAttack(client, strict, false)) return;
        }

        var stack = player.getMainHandItem();
        if (!stack.isItemEnabled(client.level.enabledFeatures()) || player.cannotAttackWithItem(stack, 0)) return;
        if (player.isUsingItem() && multiActions.attackWhileUsing(client, () -> AttackUtils.attack(client, target))) return;
        if (player.isUsingItem() && !multiActions.holdsWhileAttacking(player)) return;
        AttackUtils.attack(client, target);
    }
}
