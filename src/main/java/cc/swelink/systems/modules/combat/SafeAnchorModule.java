package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.settings.HeartsSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.AnchorDamage;
import cc.swelink.utils.combat.Targets;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationUtils;
import cc.swelink.utils.world.BlockInteractionController;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.HashSet;
import java.util.Set;

public final class SafeAnchorModule extends Module {
    private static final float WORTH_IT = 0.5f;
    private static final int GIVE_UP_TICKS = 20;
    private static final int ANCHOR_GIVE_UP_TICKS = 60;
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Smart", "Smart", "Always"));
    private final HeartsSetting minDamage = addSetting(new HeartsSetting("Min damage", 4, 0, 40));
    private final HeartsSetting maxDamage = addSetting(new HeartsSetting("Max damage", 12, 0, 40));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final BlockInteractionController<Target> interaction = new BlockInteractionController<>(
            this, this::isEnabled, this::addSetting, true) {
        @Override
        protected Target findTarget(Minecraft client) {
            var plan = plan(client);
            return plan == null ? null : plan.shield();
        }

        @Override
        protected boolean validTarget(Minecraft client, Target target) {
            return SafeAnchorModule.this.validTarget(client, target);
        }

        @Override
        protected boolean matches(Target target, BlockHitResult hit) {
            return SafeAnchorModule.matches(target, hit);
        }

        @Override
        protected int rotationPriority() {
            return 5;
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
            return trace(client, client.player.getEyePosition(), rotation.direction(), target.hit().getBlockPos());
        }
    };
    private final Set<BlockPos> failedSpots = new HashSet<>();
    private BlockPos lockedAnchor;
    private BlockPos shieldedAnchor;
    private int lockedSince;
    private boolean shieldAbandoned;
    private BlockPos pendingSpot;
    private int pendingSince;
    private Plan cached;
    private long frame;
    private long cachedFrame = Long.MIN_VALUE;

    private record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item)
            implements BlockInteractionController.BlockTarget {
    }

    private record Plan(BlockPos anchor, Target shield, boolean allow) {
    }

    public SafeAnchorModule() {
        super("SafeAnchor", "Covers your anchor with glowstone", Category.COMBAT);
        interaction.setDefaultDelays(0, 0);
        minDamage.visibleWhen(() -> mode.get().equals("Smart"));
        maxDamage.visibleWhen(() -> mode.get().equals("Smart"));
    }

    public boolean busy(Minecraft client) {
        if (!isEnabled()) return false;
        var plan = plan(client);
        return plan != null && plan.shield() != null;
    }

    public boolean allowsDetonation(Minecraft client, BlockPos anchor) {
        if (!isEnabled()) return true;
        var plan = plan(client);
        return plan == null || !plan.anchor().equals(anchor) || plan.allow();
    }

    private Plan plan(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null) return null;
        if (cachedFrame == frame) return cached;
        cachedFrame = frame;
        cached = computePlan(client);
        return cached;
    }

    private Plan computePlan(Minecraft client) {
        var player = client.player;
        BlockPos auraAnchor = Paradox.MODULES.get(AnchorAuraModule.class).detonating();
        BlockPos anchor = auraAnchor != null ? auraAnchor : anchorInSight(client);
        if (anchor == null && lockedAnchor != null && pendingSpot != null
                && client.level.getBlockState(lockedAnchor).is(Blocks.RESPAWN_ANCHOR)
                && player.isWithinBlockInteractionRange(lockedAnchor, 0)) {
            anchor = lockedAnchor;
        }
        if (shieldedAnchor != null && !client.level.getBlockState(shieldedAnchor).is(Blocks.RESPAWN_ANCHOR)) {
            shieldedAnchor = null;
        }
        if (anchor == null) {
            lockedAnchor = null;
            pendingSpot = null;
            failedSpots.clear();
            shieldAbandoned = false;
            return null;
        }
        if (!anchor.equals(lockedAnchor)) {
            lockedAnchor = anchor;
            lockedSince = player.tickCount;
            shieldAbandoned = false;
            pendingSpot = null;
            failedSpots.clear();
        }
        if (client.level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, anchor)) {
            return new Plan(anchor, null, true);
        }
        if (anchor.equals(shieldedAnchor)) {
            boolean safe = mode.get().equals("Always") || AnchorDamage.harmless(player)
                    || AnchorDamage.healthDamage(client, player, anchor, null) <= maxDamage.get();
            return new Plan(anchor, null, safe);
        }
        float unshieldedBlast = AnchorDamage.blastDamage(player, anchor, null);
        Player enemy = enemy(client, anchor);
        Target best = null;
        float bestBlast = Float.MAX_VALUE;
        float bestEnemy = -1;
        boolean haveGlowstone = InventoryUtils.findHotbar(player.getInventory(), Items.GLOWSTONE) >= 0;
        if (haveGlowstone && !shieldAbandoned) {
            for (BlockPos spot : BlockPos.betweenClosed(anchor.offset(-1, -1, -1), anchor.offset(1, 1, 1))) {
                if (spot.equals(anchor) || failedSpots.contains(spot)) continue;
                Target target = placement(client, anchor, spot.immutable());
                if (target == null) continue;
                float self = AnchorDamage.blastDamage(player, anchor, target.position());
                float other = enemy == null ? 0 : AnchorDamage.blastDamage(enemy, anchor, target.position());
                if (self < bestBlast - 0.01f || Math.abs(self - bestBlast) <= 0.01f && other > bestEnemy) {
                    best = target;
                    bestBlast = self;
                    bestEnemy = other;
                }
            }
        }
        boolean blocksBlast = best != null && unshieldedBlast - bestBlast >= WORTH_IT;
        if (mode.get().equals("Always")) {
            return blocksBlast ? new Plan(anchor, best, false) : new Plan(anchor, null, true);
        }
        float unshielded = AnchorDamage.healthDamage(client, player, anchor, null);
        if (unshielded <= minDamage.get()) return new Plan(anchor, null, true);
        boolean improves = blocksBlast && unshielded - AnchorDamage.healthDamage(client, player, anchor,
                best.position()) >= WORTH_IT;
        if (improves) return new Plan(anchor, best, false);
        return new Plan(anchor, null, true);
    }

    private static BlockPos anchorInSight(Minecraft client) {
        var player = client.player;
        if (!(player.raycastHitResult(1, player) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !player.isWithinBlockInteractionRange(hit.getBlockPos(), 0)
                || !client.level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) return null;
        return hit.getBlockPos().immutable();
    }

    private static Player enemy(Minecraft client, BlockPos anchor) {
        Vec3 center = Vec3.atCenterOf(anchor);
        Player best = null;
        double bestDistance = 100;
        for (var player : client.level.players()) {
            if (!Targets.attackable(client, player)) continue;
            double distance = player.distanceToSqr(center);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Target placement(Minecraft client, BlockPos anchor, BlockPos spot) {
        var level = client.level;
        BlockState current = level.getBlockState(spot);
        boolean fire = current.getBlock() instanceof BaseFireBlock;
        if (!current.isAir() && !fire && !(current.getBlock() instanceof LiquidBlock)
                || !level.isUnobstructed(Blocks.GLOWSTONE.defaultBlockState(), spot, CollisionContext.empty())) return null;
        var eyes = client.player.getEyePosition();
        double reach = client.player.blockInteractionRange();
        var outline = current.getShape(level, spot);
        if (fire && !outline.isEmpty()) {
            Vec3 aim = outline.bounds().getCenter().add(spot.getX(), spot.getY(), spot.getZ());
            if (eyes.distanceTo(aim) <= reach) {
                var hit = trace(client, eyes, aim.subtract(eyes).normalize(), spot);
                if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(spot)) {
                    return new Target(spot.immutable(), hit, hit.getLocation(), Items.GLOWSTONE);
                }
            }
        }
        for (Direction direction : Direction.values()) {
            BlockPos support = spot.relative(direction);
            if (support.equals(anchor)) continue;
            BlockState state = level.getBlockState(support);
            if (state.canBeReplaced() || state.getShape(level, support).isEmpty() || usable(state)) continue;
            Vec3 aim = Vec3.atCenterOf(spot).add(direction.getStepX() * 0.5, direction.getStepY() * 0.5,
                    direction.getStepZ() * 0.5);
            if (eyes.distanceTo(aim) > reach) continue;
            var hit = trace(client, eyes, aim.subtract(eyes).normalize(), support);
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                    && hit.getBlockPos().relative(hit.getDirection()).equals(spot)) {
                return new Target(spot.immutable(), hit, hit.getLocation(), Items.GLOWSTONE);
            }
        }
        return null;
    }

    static boolean usable(BlockState state) {
        return state.getBlock() instanceof RespawnAnchorBlock || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.BUTTONS) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.BEDS)
                || state.is(BlockTags.SHULKER_BOXES) || state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.ENDER_CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.CRAFTING_TABLE)
                || state.is(Blocks.FURNACE) || state.is(Blocks.LEVER) || state.is(Blocks.ANVIL)
                || state.is(Blocks.ENCHANTING_TABLE) || state.is(Blocks.NOTE_BLOCK) || state.is(Blocks.LECTERN);
    }

    private boolean validTarget(Minecraft client, Target target) {
        var plan = plan(client);
        if (plan == null || plan.shield() == null || !plan.shield().position().equals(target.position())) return false;
        var eyes = client.player.getEyePosition();
        return matches(target, trace(client, eyes, RotationUtils.lookAt(eyes, target.aim()).direction(), target.hit().getBlockPos()));
    }

    private BlockHitResult trace(Minecraft client, Vec3 eyes, Vec3 direction, BlockPos target) {
        Vec3 end = eyes.add(direction.scale(client.player.blockInteractionRange()));
        if (raycast.target()) return RotationRaycast.block(eyes, end, target);
        return client.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
    }

    private static boolean matches(Target target, BlockHitResult hit) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(target.hit().getBlockPos())
                && hit.getDirection() == target.hit().getDirection();
    }

    private static boolean wouldDetonate(Minecraft client, BlockState state) {
        int charge = state.getValue(RespawnAnchorBlock.CHARGE);
        if (charge == 0) return false;
        var player = client.player;
        boolean fuel = player.getMainHandItem().is(Items.GLOWSTONE) || player.getOffhandItem().is(Items.GLOWSTONE);
        return !(fuel && charge < RespawnAnchorBlock.MAX_CHARGES);
    }

    private void manualUse(ManualUseEvent event) {
        var client = event.client();
        if (!interaction.allowManualUse(client)) {
            event.cancel();
            return;
        }
        if (!isEnabled() || client.player == null || client.level == null
                || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var state = client.level.getBlockState(hit.getBlockPos());
        if (state.is(Blocks.RESPAWN_ANCHOR) && wouldDetonate(client, state)
                && (busy(client) || !allowsDetonation(client, hit.getBlockPos()))) {
            event.cancel();
        }
    }

    @Override
    public void tick(Minecraft client) {
        interaction.tick(client);
    }

    @Override
    public void tickInput(Minecraft client) {
        var plan = plan(client);
        BlockPos wanted = plan == null || plan.shield() == null ? null : plan.shield().position();
        if (client.player != null && wanted != null && !wanted.equals(pendingSpot)) {
            pendingSpot = wanted;
            pendingSince = client.player.tickCount;
        } else if (wanted == null) {
            pendingSpot = null;
        }
        interaction.tickInput(client);
        if (pendingSpot != null && !client.level.getBlockState(pendingSpot).canBeReplaced()) {
            shieldedAnchor = lockedAnchor;
            pendingSpot = null;
            cachedFrame = Long.MIN_VALUE;
            return;
        }
        if (pendingSpot != null && lockedAnchor != null && client.player.tickCount - lockedSince >= ANCHOR_GIVE_UP_TICKS) {
            shieldAbandoned = true;
            pendingSpot = null;
            cachedFrame = Long.MIN_VALUE;
            return;
        }
        if (pendingSpot != null && client.player.tickCount - pendingSince >= GIVE_UP_TICKS
                && client.level.getBlockState(pendingSpot).canBeReplaced()) {
            failedSpots.add(pendingSpot);
            pendingSpot = null;
            cachedFrame = Long.MIN_VALUE;
        }
    }

    @Override
    protected void onDisable() {
        interaction.disable();
        lockedAnchor = null;
        shieldedAnchor = null;
        shieldAbandoned = false;
        pendingSpot = null;
        failedSpots.clear();
        cached = null;
        cachedFrame = Long.MIN_VALUE;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 59)
    private void onPreTick(PreTickEvent event) {
        frame++;
    }

    @EventHandler(priority = 215)
    private void onPreTick2(PreTickEvent event) {
        interaction.tickSwap(event.client());
    }

    @EventHandler(priority = 35)
    private void onManualUse(ManualUseEvent event) {
        manualUse(event);
    }

    @EventHandler(priority = 35)
    private void onTickBoundary(TickBoundaryEvent event) {
        interaction.afterTickBoundary();
    }
}
