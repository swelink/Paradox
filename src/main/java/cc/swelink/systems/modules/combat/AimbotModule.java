package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.AttackUtils;
import cc.swelink.utils.combat.TargetFinder;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.rotation.RotationSettings;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class AimbotModule extends Module {
    private final IntSetting range = addSetting(new IntSetting("Range", 3, 1, 6));
    private final IntSetting fov = addSetting(new IntSetting("FOV", 360, 10, 360));
    private final ModeSetting aimMode = addSetting(new ModeSetting("Aim mode", "Multipoint", "Multipoint", "Head"));
    private final RotationSettings rotations = new RotationSettings(this::addSetting);
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final PercentSetting aimInset = addSetting(new PercentSetting("Aim inset", 25));

    public AimbotModule() {
        super("AimBot", "Aims at players", Category.COMBAT);
        aimInset.visibleWhen(() -> aimMode.get().equals("Multipoint"));
    }

    @Override
    public void tick(Minecraft client) {
        var target = target(client);
        if (target == null) return;
        Paradox.TARGETS.report(target);
        var eyes = client.player.getEyePosition();
        rotations.request(client, RotationUtils.lookAt(eyes, aimPoint(target, eyes)), 1);
    }

    @Override
    protected void onDisable() {
        Paradox.ROTATIONS.clear();
    }

    private Player target(Minecraft client) {
        if (!Gameplay.canAct(client)) return null;
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
}
