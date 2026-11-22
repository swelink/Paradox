package cc.swelink.systems.modules.world;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.Targets;
import cc.swelink.utils.combat.WebCatchPrediction;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationUtils;
import cc.swelink.utils.world.BlockInteractionController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AutoWebModule extends Module {
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final BlockInteractionController<Target> interaction = new BlockInteractionController<>(
            this, this::isEnabled, this::addSetting, true) {
        @Override
        protected Target findTarget(Minecraft client) {
            return AutoWebModule.this.findTarget(client);
        }

        @Override
        protected boolean validTarget(Minecraft client, Target target) {
            return AutoWebModule.this.validTarget(client, target);
        }

        @Override
        protected boolean matches(Target target, BlockHitResult hit) {
            return AutoWebModule.this.matches(target, hit);
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
            return AutoWebModule.this.traceTarget(client, target, rotation);
        }
    };

    private final Map<Player, Vec3> previousPositions = new HashMap<>();
    private final Map<Player, Vec3> motion = new HashMap<>();
    private final Map<Player, Integer> launched = new HashMap<>();
    private final Set<Player> caught = new HashSet<>();
    private ClientLevel trackedLevel;

    private record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item, Player player) implements BlockInteractionController.BlockTarget {
        private Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item) {
            this(position, hit, aim, item, null);
        }
    }

    public AutoWebModule() {
        super("AutoWeb", "Webs players in the air", Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        interaction.disable();
        clearTracking();
    }

    public void tickSwap(Minecraft client) {
        interaction.tickSwap(client);
        trackPlayers(client);
    }

    @Override
    public void tick(Minecraft client) {
        interaction.tick(client);
    }

    @Override
    public void tickInput(Minecraft client) {
        interaction.tickInput(client);
    }

    public boolean allowManualUse(Minecraft client) {
        return interaction.allowManualUse(client);
    }

    public void afterTickBoundary() {
        interaction.afterTickBoundary();
    }

    private BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
        return RotationRaycast.blocks(rotation, client.player.blockInteractionRange(),
                ClipContext.Fluid.NONE, only(target.hit().getBlockPos()));
    }

    private BlockPos only(BlockPos pos) {
        return raycast.target() ? pos : null;
    }

    private Target findTarget(Minecraft client) {
        if (InventoryUtils.findHotbar(client.player.getInventory(), Items.COBWEB) < 0) {
            return null;
        }
        var eyes = client.player.getEyePosition();
        Target closest = null;
        boolean bestBehind = false;
        double bestCatchDistance = Double.POSITIVE_INFINITY;
        int earliest = Integer.MAX_VALUE;
        double distance = Double.POSITIVE_INFINITY;
        for (var player : client.level.players()) {
            if (!launched.containsKey(player) || caught.contains(player) || !Targets.attackable(client, player)
                    || !Targets.visible(client, player, raycast.target())) {
                continue;
            }
            var path = catchPath(player);
            var movement = motion.getOrDefault(player, player.getDeltaMovement());
            var rear = WebCatchPrediction.behindPoint(path.getFirst().from(), movement,
                    player.position().subtract(client.player.position()));
            var center = player.getBoundingBox().getCenter();
            var rearDirection = new Vec3(rear.x - center.x, 0, rear.z - center.z).normalize();
            for (int stepIndex = 0; stepIndex < path.size(); stepIndex++) {
                var step = path.get(stepIndex);
                var predicted = step.from().minmax(step.to());
                var minimum = BlockPos.containing(predicted.minX, predicted.minY, predicted.minZ);
                var maximum = BlockPos.containing(predicted.maxX - 1.0e-7, predicted.maxY - 1.0e-7, predicted.maxZ - 1.0e-7);
                for (var pos : BlockPos.betweenClosed(minimum, maximum)) {
                    if (!client.player.isWithinBlockInteractionRange(pos, 0)
                            || !client.level.getBlockState(pos).canBeReplaced()
                            || !WebCatchPrediction.intersects(new AABB(pos), step)) {
                        continue;
                    }
                    var placement = webTarget(client, pos);
                    if (placement == null) {
                        continue;
                    }
                    var candidate = new Target(placement.position(), placement.hit(), placement.aim(), Items.COBWEB, player);
                    if (!validTarget(client, candidate)) {
                        continue;
                    }
                    var blockCenter = Vec3.atCenterOf(pos);
                    boolean behind = new Vec3(blockCenter.x - center.x, 0, blockCenter.z - center.z)
                            .dot(rearDirection) > 0;
                    double horizontalDistance = Math.pow(blockCenter.x - rear.x, 2) + Math.pow(blockCenter.z - rear.z, 2);
                    double catchDistance = horizontalDistance + Math.pow(pos.getY() - rear.y, 2) * 0.25;
                    double candidateDistance = eyes.distanceToSqr(candidate.aim());
                    if (closest == null || behind && !bestBehind
                            || behind == bestBehind && (catchDistance < bestCatchDistance - 1.0e-7
                            || Math.abs(catchDistance - bestCatchDistance) < 1.0e-7
                            && (stepIndex < earliest || stepIndex == earliest && candidateDistance < distance))) {
                        closest = candidate;
                        bestBehind = behind;
                        bestCatchDistance = catchDistance;
                        earliest = stepIndex;
                        distance = candidateDistance;
                    }
                }
            }
        }
        if (closest != null) Paradox.TARGETS.report(closest.player());
        return closest;
    }

    private Target webTarget(Minecraft client, BlockPos pos) {
        for (var direction : Direction.values()) {
            var support = pos.relative(direction);
            var state = client.level.getBlockState(support);
            if (state.canBeReplaced() || state.getShape(client.level, support).isEmpty()) {
                continue;
            }
            var aim = Vec3.atCenterOf(pos).add(direction.getStepX() * 0.5,
                    direction.getStepY() * 0.5, direction.getStepZ() * 0.5);
            var hit = RotationRaycast.blocks(RotationUtils.lookAt(client.player.getEyePosition(), aim),
                    client.player.blockInteractionRange(), ClipContext.Fluid.NONE, only(support));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                    && hit.getBlockPos().relative(hit.getDirection()).equals(pos)) {
                return new Target(pos.immutable(), hit, hit.getLocation(), Items.COBWEB);
            }
        }
        return null;
    }

    private boolean validTarget(Minecraft client, Target target) {
        var player = target.player();
        return player != null && player != client.player && player.level() == client.level
                && player.isAlive() && !player.isSpectator() && !player.onGround()
                && launched.containsKey(player) && !caught.contains(player)
                && client.level.getBlockState(target.position()).canBeReplaced()
                && catchPath(player).stream().anyMatch(step -> WebCatchPrediction.intersects(new AABB(target.position()), step))
                && visible(client, target);
    }

    private boolean matches(Target target, BlockHitResult hit) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(target.hit().getBlockPos())
                && hit.getDirection() == target.hit().getDirection();
    }

    private boolean visible(Minecraft client, Target target) {
        return matches(target, RotationRaycast.blocks(RotationUtils.lookAt(client.player.getEyePosition(), target.aim()),
                client.player.blockInteractionRange(),
                ClipContext.Fluid.NONE, only(target.hit().getBlockPos())));
    }

    private void trackPlayers(Minecraft client) {
        if (!isEnabled() || client.player == null || client.level == null) {
            clearTracking();
            return;
        }
        if (trackedLevel != client.level) {
            clearTracking();
            trackedLevel = client.level;
        }
        var players = client.level.players();
        previousPositions.keySet().retainAll(players);
        motion.keySet().retainAll(players);
        launched.keySet().retainAll(players);
        caught.retainAll(players);
        for (var player : players) {
            if (player == client.player) {
                continue;
            }
            var previous = previousPositions.put(player, player.position());
            var movement = previous == null ? player.getDeltaMovement() : player.position().subtract(previous);
            if (movement.lengthSqr() < 1.0e-8) {
                movement = player.getDeltaMovement();
            }
            motion.put(player, movement);
            if (inCobweb(client, player)) {
                caught.add(player);
                launched.remove(player);
                continue;
            }
            if (!player.isAlive() || player.isSpectator() || player.onGround()) {
                launched.remove(player);
                caught.remove(player);
                continue;
            }
            var remaining = launched.get(player);
            if (remaining != null) {
                if (remaining <= 1) {
                    launched.remove(player);
                } else {
                    launched.put(player, remaining - 1);
                }
            }
            if (player.hurtTime > 0 && movement.y > 0.03 && !caught.contains(player)) {
                launched.putIfAbsent(player, 20);
            }
        }
    }

    private List<WebCatchPrediction.Step> catchPath(Player player) {
        var movement = motion.getOrDefault(player, player.getDeltaMovement());
        int wait = interaction.predictionWaitTicks();
        return WebCatchPrediction.path(player.getBoundingBox(), movement, player.getGravity(), wait);
    }

    private static boolean inCobweb(Minecraft client, Player player) {
        var box = player.getBoundingBox().deflate(1.0e-7);
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (client.level.getBlockState(pos).is(Blocks.COBWEB) && new AABB(pos).intersects(box)) {
                return true;
            }
        }
        return false;
    }

    private void clearTracking() {
        previousPositions.clear();
        motion.clear();
        launched.clear();
        caught.clear();
        trackedLevel = null;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 200)
    private void onPreTick(PreTickEvent event) {
        tickSwap(event.client());
    }

    @EventHandler(priority = 20)
    private void onManualUse(ManualUseEvent event) {
        if (!allowManualUse(event.client())) event.cancel();
    }

    @EventHandler(priority = 20)
    private void onTickBoundary(TickBoundaryEvent event) {
        afterTickBoundary();
    }
}
