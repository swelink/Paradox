package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
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
import cc.swelink.utils.combat.Targets;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class TriggerbotModule extends Module {
    private final ModeSetting critMode = addSetting(new ModeSetting("Crit mode", "Off", "Off", "Smart", "Strict"));
    private final PercentSetting swordCooldown = addSetting(new PercentSetting("Sword cooldown", 90));
    private final PercentSetting itemCooldown = addSetting(new PercentSetting("Item cooldown", 100));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());

    public TriggerbotModule() {
        super("TriggerBot", "Hits players you look at", Category.COMBAT);
    }

    @Override
    public void tickInput(Minecraft client) {
        if (!Gameplay.canAct(client) || client.gameMode == null || client.player.isHandsBusy() || SlotSelection.isOwned()) {
            return;
        }
        var rotation = RotationRaycast.interactionRotation(client, 1);
        attack(client, raycast.target() ? nearestTrace(client, rotation) : client.hitResult);
    }

    @Override
    protected void onDisable() {
        CriticalHitTiming.resetSprint();
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

    private static EntityHitResult nearestTrace(Minecraft client, Rotation rotation) {
        var eyes = client.player.getEyePosition();
        EntityHitResult best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player candidate : client.level.players()) {
            if (candidate == client.player || !Targets.attackable(client, candidate)) continue;
            var hit = EnemyRaycast.trace(client, candidate, rotation);
            if (hit == null) continue;
            double distance = hit.getLocation().distanceToSqr(eyes);
            if (distance < bestDistance) {
                best = hit;
                bestDistance = distance;
            }
        }
        return best;
    }
}
