package cc.swelink.utils.combat;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.StickyTargetModule;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class TargetFinder {
    private TargetFinder() {
    }

    public static boolean eligible(Minecraft client, Player target, boolean throughWalls) {
        return target != null && Targets.attackable(client, target) && target.isPickable()
                && Paradox.MODULES.get(StickyTargetModule.class).allows(client, target)
                && Targets.visible(client, target, throughWalls);
    }

    public static Player nearest(Minecraft client, boolean throughWalls, Predicate<Player> filter, ToDoubleFunction<Player> distance) {
        Player nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (var target : client.level.players()) {
            if (!filter.test(target) || !eligible(client, target, throughWalls)) continue;
            double value = distance.applyAsDouble(target);
            if (value < nearestDistance) {
                nearest = target;
                nearestDistance = value;
            }
        }
        return nearest;
    }

    public static List<Player> sorted(Minecraft client, boolean throughWalls, Predicate<Player> filter, ToDoubleFunction<Player> distance) {
        var targets = new ArrayList<Player>();
        for (var target : client.level.players()) {
            if (filter.test(target) && eligible(client, target, throughWalls)) targets.add(target);
        }
        targets.sort(Comparator.comparingDouble(distance));
        return targets;
    }

    public static double hitboxDistanceSqr(Vec3 eyes, Player target) {
        return eyes.distanceToSqr(RotationUtils.nearestPoint(eyes, target.getBoundingBox()));
    }
}
