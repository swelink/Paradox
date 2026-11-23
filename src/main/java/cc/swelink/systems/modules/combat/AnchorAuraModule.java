package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.AnchorDamage;
import cc.swelink.utils.combat.TargetFinder;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationUtils;
import cc.swelink.utils.world.BlockInteractionController;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AnchorAuraModule extends Module {
    private static final double NEXT_TO_ENEMY = 2;
    private static final double PLAYER_MARGIN = 0.3;
    private static final int PREDICT_TICKS = 3;
    private static final double ANCHOR_SEARCH = 3;
    private static final float MIN_BLAST = 1;
    private final IntSetting explodeSlot = addSetting(new IntSetting("Explode slot", 1, 1, 9).bounded());
    private final IntSetting explodeDelay = addSetting(new IntSetting("Explode delay", 1, 0, 20));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final BlockInteractionController<Target> interaction = new BlockInteractionController<>(
            this, this::isEnabled, this::addSetting, true) {
        @Override
        protected Target findTarget(Minecraft client) {
            return AnchorAuraModule.this.findTarget(client);
        }

        @Override
        protected boolean validTarget(Minecraft client, Target target) {
            return AnchorAuraModule.this.validTarget(client, target);
        }

        @Override
        protected boolean matches(Target target, BlockHitResult hit) {
            return AnchorAuraModule.matches(target, hit);
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
            return RotationRaycast.blocks(rotation, client.player.blockInteractionRange(), ClipContext.Fluid.NONE,
                    only(target.hit().getBlockPos()));
        }

        @Override
        protected int rotationPriority() {
            return 4;
        }

        @Override
        protected boolean keepAimingBetweenInteractions() {
            return true;
        }
    };
    private BlockPos detonating;
    private BlockPos charged;
    private int chargedAt;
    private float planDamage;
    private float potential;
    private int potentialTick = -1;

    private record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item, boolean placing, int slot)
            implements BlockInteractionController.BlockTarget {
        Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item, boolean placing) {
            this(position, hit, aim, item, placing, -1);
        }
    }

    public AnchorAuraModule() {
        super("AnchorAura", "Anchors close to players", Category.COMBAT);
    }

    public BlockPos detonating() {
        return isEnabled() ? detonating : null;
    }

    public float potential(Minecraft client) {
        if (!isEnabled() || client.player == null || client.level == null) return 0;
        if (potentialTick != client.player.tickCount) {
            BlockPos previous = detonating;
            plan(client);
            detonating = previous;
            remember(client);
        }
        return potential;
    }

    private void remember(Minecraft client) {
        potentialTick = client.player.tickCount;
        potential = planDamage;
    }

    private Target findTarget(Minecraft client) {
        Target target = plan(client);
        remember(client);
        if (target != null && Paradox.MODULES.get(CrystalAuraModule.class).potential(client) > planDamage) {
            detonating = null;
            return null;
        }
        return target;
    }

    private Target plan(Minecraft client) {
        var player = client.player;
        detonating = null;
        planDamage = 0;
        Player enemy = closestEnemy(client, raycast.target());
        if (enemy == null) return null;
        var spots = spots(enemy);
        if (client.level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, enemy.blockPosition())) return null;
        for (BlockPos anchor : nearbyAnchors(client, enemy)) {
            BlockHitResult hit = blockHit(client, anchor, only(anchor) != null);
            if (hit != null) {
                Paradox.TARGETS.report(enemy);
                return anchorTarget(client, enemy, anchor, hit);
            }
        }
        if (InventoryUtils.findHotbar(player.getInventory(), Items.RESPAWN_ANCHOR) < 0) return null;
        if (Paradox.MODULES.get(SafeAnchorModule.class).busy(client)) return null;
        for (BlockPos spot : spots) {
            Target target = placement(client, spot);
            if (target != null) {
                Paradox.TARGETS.report(enemy);
                planDamage = AnchorDamage.healthDamage(client, enemy, spot, null);
                return target;
            }
        }
        return null;
    }

    private Target anchorTarget(Minecraft client, Player enemy, BlockPos anchor, BlockHitResult hit) {
        var player = client.player;
        int charge = client.level.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE);
        if (charge == 0) {
            charged = null;
            if (InventoryUtils.findHotbar(player.getInventory(), Items.GLOWSTONE) < 0) return null;
            planDamage = AnchorDamage.healthDamage(client, enemy, anchor, null);
            if (Paradox.MODULES.get(SafeAnchorModule.class).busy(client)) return null;
            return new Target(anchor, hit, hit.getLocation(), Items.GLOWSTONE, false);
        }
        planDamage = AnchorDamage.healthDamage(client, enemy, anchor, null);
        if (!anchor.equals(charged)) {
            charged = anchor;
            chargedAt = player.tickCount;
        }
        Item item = player.getInventory().getItem(explodeSlot.get() - 1).getItem();
        if (item == Items.GLOWSTONE || player.getOffhandItem().is(Items.GLOWSTONE)
                && charge < RespawnAnchorBlock.MAX_CHARGES) return null;
        detonating = anchor;
        var safeAnchor = Paradox.MODULES.get(SafeAnchorModule.class);
        if (safeAnchor.busy(client) || !safeAnchor.allowsDetonation(client, anchor)
                || player.tickCount - chargedAt < explodeDelay.get()) return null;
        return new Target(anchor, hit, hit.getLocation(), item, false, explodeSlot.get() - 1);
    }

    static Player closestEnemy(Minecraft client, boolean throughWalls) {
        double reach = client.player.blockInteractionRange() + NEXT_TO_ENEMY;
        double limit = reach * reach;
        return TargetFinder.nearest(client, throughWalls, player -> client.player.distanceToSqr(player) <= limit,
                client.player::distanceToSqr);
    }

    private static List<BlockPos> spots(Player enemy) {
        AABB box = enemy.getBoundingBox();
        AABB area = occupied(enemy);
        Vec3 center = box.getCenter();
        var spots = new ArrayList<BlockPos>();
        var minimum = BlockPos.containing(area.minX - 1, area.minY - 1, area.minZ - 1);
        var maximum = BlockPos.containing(area.maxX + 1, area.maxY + 1, area.maxZ + 1);
        for (BlockPos pos : BlockPos.betweenClosed(minimum, maximum)) {
            if (!new AABB(pos).intersects(box)) spots.add(pos.immutable());
        }
        spots.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(center)));
        return spots;
    }

    private static List<BlockPos> nearbyAnchors(Minecraft client, Player enemy) {
        AABB box = enemy.getBoundingBox();
        Vec3 center = box.getCenter();
        AABB area = box.inflate(ANCHOR_SEARCH);
        var anchors = new ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ),
                BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
            if (client.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)
                    && AnchorDamage.blastDamage(enemy, pos, null) > MIN_BLAST) anchors.add(pos.immutable());
        }
        anchors.sort(Comparator.comparing((BlockPos pos) -> client.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGE) == 0)
                .thenComparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(center)));
        return anchors;
    }

    private static AABB occupied(Player player) {
        Vec3 motion = player.position().subtract(player.xo, player.yo, player.zo);
        return player.getBoundingBox().expandTowards(motion.scale(PREDICT_TICKS)).inflate(PLAYER_MARGIN);
    }

    private BlockPos only(BlockPos pos) {
        return raycast.target() ? pos : null;
    }

    static boolean clearOfPlayers(Minecraft client, BlockPos spot) {
        AABB block = new AABB(spot);
        for (var player : client.level.players()) {
            if (player != client.player && !player.isSpectator() && block.intersects(occupied(player))) return false;
        }
        return true;
    }

    static BlockHitResult blockHit(Minecraft client, BlockPos anchor, boolean enemy) {
        var eyes = client.player.getEyePosition();
        BlockHitResult best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            Vec3 aim = Vec3.atCenterOf(anchor).add(direction.getStepX() * 0.5, direction.getStepY() * 0.5, direction.getStepZ() * 0.5);
            var hit = RotationRaycast.blocks(RotationUtils.lookAt(eyes, aim), client.player.blockInteractionRange(), ClipContext.Fluid.NONE,
                    enemy ? anchor : null);
            if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(anchor)) continue;
            double distance = eyes.distanceToSqr(hit.getLocation());
            if (distance < bestDistance) {
                best = hit;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Target placement(Minecraft client, BlockPos spot) {
        BlockHitResult hit = placementHit(client, spot, Blocks.RESPAWN_ANCHOR.defaultBlockState(), only(spot) != null);
        return hit == null ? null : new Target(spot, hit, hit.getLocation(), Items.RESPAWN_ANCHOR, true);
    }

    static BlockHitResult placementHit(Minecraft client, BlockPos spot, BlockState placed, boolean enemy) {
        var level = client.level;
        if (!level.getBlockState(spot).canBeReplaced() || !clearOfPlayers(client, spot)
                || !level.isUnobstructed(placed, spot, CollisionContext.empty())) return null;
        var eyes = client.player.getEyePosition();
        for (Direction direction : Direction.values()) {
            BlockPos support = spot.relative(direction);
            var state = level.getBlockState(support);
            if (state.canBeReplaced() || state.getShape(level, support).isEmpty() || SafeAnchorModule.usable(state)) continue;
            Vec3 aim = Vec3.atCenterOf(spot).add(direction.getStepX() * 0.5, direction.getStepY() * 0.5, direction.getStepZ() * 0.5);
            var hit = RotationRaycast.blocks(RotationUtils.lookAt(eyes, aim), client.player.blockInteractionRange(), ClipContext.Fluid.NONE,
                    enemy ? support : null);
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                    && hit.getBlockPos().relative(hit.getDirection()).equals(spot)) return hit;
        }
        return null;
    }

    private boolean validTarget(Minecraft client, Target target) {
        var state = client.level.getBlockState(target.position());
        if (target.placing() ? !state.canBeReplaced() || !clearOfPlayers(client, target.position())
                : !state.is(Blocks.RESPAWN_ANCHOR)) return false;
        var eyes = client.player.getEyePosition();
        return matches(target, RotationRaycast.blocks(RotationUtils.lookAt(eyes, target.aim()),
                client.player.blockInteractionRange(), ClipContext.Fluid.NONE, only(target.hit().getBlockPos())));
    }

    private static boolean matches(Target target, BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(target.hit().getBlockPos())) return false;
        return !target.placing() || hit.getDirection() == target.hit().getDirection();
    }

    @Override
    public void tick(Minecraft client) {
        interaction.tick(client);
    }

    @Override
    public void tickInput(Minecraft client) {
        interaction.tickInput(client);
    }

    private void manualUse(ManualUseEvent event) {
        if (!interaction.allowManualUse(event.client())) event.cancel();
    }

    @Override
    protected void onDisable() {
        interaction.disable();
        detonating = null;
        charged = null;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 216)
    private void onPreTick(PreTickEvent event) {
        interaction.tickSwap(event.client());
    }

    @EventHandler(priority = 36)
    private void onManualUse(ManualUseEvent event) {
        manualUse(event);
    }

    @EventHandler(priority = 36)
    private void onTickBoundary(TickBoundaryEvent event) {
        interaction.afterTickBoundary();
    }
}
