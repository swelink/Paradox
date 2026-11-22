package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.TargetFinder;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationUtils;
import cc.swelink.utils.world.BlockInteractionController;
import cc.swelink.utils.world.BlockPlacement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AutoTrapModule extends Module {
    private static final double JUMP_HEIGHT = 1.0;
    private final BooleanSetting headWeb = addSetting(new BooleanSetting("Head web", true));
    private final BooleanSetting jump = addSetting(new BooleanSetting("Jump", true));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final BlockInteractionController<Target> interaction = new BlockInteractionController<>(
            this, this::isEnabled, this::addSetting, true) {
        @Override
        protected Target findTarget(Minecraft client) {
            return AutoTrapModule.this.findTarget(client);
        }

        @Override
        protected boolean validTarget(Minecraft client, Target target) {
            return AutoTrapModule.this.validTarget(client, target);
        }

        @Override
        protected boolean matches(Target target, BlockHitResult hit) {
            return AutoTrapModule.this.matches(target, hit);
        }

        @Override
        protected int rotationPriority() {
            return 5;
        }

        @Override
        protected boolean restoreAfterUse() {
            return false;
        }

        @Override
        protected boolean keepAimingBetweenInteractions() {
            return true;
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
            return AutoTrapModule.this.trace(client, client.player.getEyePosition(), rotation.direction(),
                    target.hit().getBlockPos());
        }
    };
    private boolean wantsJump;
    private int trappingUntil = -1;
    private int jumpedAt = -100;

    private record Spot(BlockPos position, Item item, boolean helper) {
        private Spot(BlockPos position, Item item) {
            this(position, item, false);
        }
    }

    private record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item, Player player)
            implements BlockInteractionController.BlockTarget {
    }

    public AutoTrapModule() {
        super("AutoTrap", "Box players", Category.COMBAT);
        interaction.setDefaultDelays(0, 0);
    }

    @Override
    protected void onDisable() {
        interaction.disable();
        wantsJump = false;
        trappingUntil = -1;
    }

    public boolean trapping(Minecraft client) {
        return isEnabled() && client.player != null && client.player.tickCount <= trappingUntil;
    }

    public void tickSwap(Minecraft client) {
        interaction.tickSwap(client);
    }

    @Override
    public void tick(Minecraft client) {
        interaction.tick(client);
        if (trapping(client) && !interaction.hasTarget()) {
            var held = Paradox.ROTATIONS.packetRotation();
            if (held != null) Paradox.ROTATIONS.requestSilent(held, 180, 5, true);
        }
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

    public Input prepareInput(Minecraft client, Input input) {
        if (!isEnabled() || !jump.get() || !wantsJump || client.player == null || !client.player.onGround()) return input;
        jumpedAt = client.player.tickCount;
        return new Input(input.forward(), input.backward(), input.left(), input.right(), true, input.shift(), input.sprint());
    }

    private Player victim(Minecraft client) {
        return TargetFinder.nearest(client, true, player -> inReach(client, player), client.player::distanceToSqr);
    }

    private static boolean inReach(Minecraft client, Player player) {
        Vec3 eyes = client.player.getEyePosition();
        double reach = client.player.blockInteractionRange();
        return eyes.distanceToSqr(RotationUtils.nearestPoint(eyes, player.getBoundingBox())) <= reach * reach;
    }

    private static boolean usable(Minecraft client, Player player) {
        return TargetFinder.eligible(client, player, true);
    }

    private List<Spot> plan(Minecraft client, Player player) {
        var box = player.getBoundingBox();
        int feetY = Mth.floor(player.getY() + 0.2);
        Set<Long> columns = new LinkedHashSet<>();
        for (int x = Mth.floor(box.minX + 1.0E-4); x <= Mth.floor(box.maxX - 1.0E-4); x++) {
            for (int z = Mth.floor(box.minZ + 1.0E-4); z <= Mth.floor(box.maxZ - 1.0E-4); z++) {
                columns.add(BlockPos.asLong(x, 0, z));
            }
        }
        List<BlockPos> footprint = new ArrayList<>();
        for (long column : columns) footprint.add(BlockPos.of(column).atY(feetY));
        Item wall = wallItem(client);
        List<Spot> spots = new ArrayList<>();
        for (BlockPos feet : footprint) spots.add(new Spot(feet, Items.COBWEB));
        if (headWeb.get()) for (BlockPos feet : footprint) spots.add(new Spot(feet.above(), Items.COBWEB));
        List<BlockPos> walls = new ArrayList<>();
        for (BlockPos feet : footprint) {
            for (var direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = feet.relative(direction);
                if (!columns.contains(BlockPos.asLong(side.getX(), 0, side.getZ())) && !walls.contains(side)) walls.add(side);
            }
        }
        for (BlockPos side : walls) spots.add(new Spot(side, wall));
        for (BlockPos side : walls) spots.add(new Spot(side.above(), wall));
        List<BlockPos> roofs = new ArrayList<>();
        for (BlockPos feet : footprint) roofs.add(feet.above(2));
        for (BlockPos roof : roofs) spots.add(new Spot(roof, wall));
        BlockPos helper = roofHelper(client, roofs, columns);
        if (helper != null) spots.add(new Spot(helper, wall, true));
        return spots;
    }

    private static Item wallItem(Minecraft client) {
        var inventory = client.player.getInventory();
        if (InventoryUtils.findHotbar(inventory, Items.OBSIDIAN) >= 0) return Items.OBSIDIAN;
        var item = BlockPlacement.placeableItem(inventory);
        return item != null ? item : Items.OBSIDIAN;
    }

    private static BlockPos roofHelper(Minecraft client, List<BlockPos> roofs, Set<Long> columns) {
        Vec3 eyes = client.player.getEyePosition();
        BlockPos best = null;
        double bestScore = -Double.MAX_VALUE;
        for (BlockPos roof : roofs) {
            if (!client.level.getBlockState(roof).canBeReplaced()) return null;
        }
        for (BlockPos roof : roofs) {
            for (var direction : Direction.Plane.HORIZONTAL) {
                BlockPos helper = roof.relative(direction);
                if (columns.contains(BlockPos.asLong(helper.getX(), 0, helper.getZ()))) continue;
                Vec3 normal = Vec3.atLowerCornerOf(direction.getOpposite().getUnitVec3i());
                Vec3 face = Vec3.atCenterOf(helper).add(normal.scale(0.5));
                double score = eyes.subtract(face).dot(normal);
                if (score > bestScore) {
                    bestScore = score;
                    best = helper;
                }
            }
        }
        return best;
    }

    private Target findTarget(Minecraft client) {
        wantsJump = false;
        var player = victim(client);
        if (player == null) return null;
        var inventory = client.player.getInventory();
        var eyes = client.player.getEyePosition();
        boolean jumpHelps = false;
        for (var spot : plan(client, player)) {
            if (!needed(client, spot) || InventoryUtils.findHotbar(inventory, spot.item()) < 0) continue;
            if (spot.helper() && (jumpHelps || client.player.tickCount - jumpedAt < 12)) continue;
            var target = placement(client, spot, player, eyes);
            if (target != null) {
                engage(client, player);
                return target;
            }
            if (!jumpHelps && client.player.onGround() && placement(client, spot, player, eyes.add(0, JUMP_HEIGHT, 0)) != null) {
                jumpHelps = true;
            }
        }
        wantsJump = jumpHelps;
        if (jumpHelps) engage(client, player);
        return null;
    }

    private void engage(Minecraft client, Player player) {
        trappingUntil = client.player.tickCount + 10;
        Paradox.TARGETS.report(player);
    }

    private static boolean needed(Minecraft client, Spot spot) {
        var state = client.level.getBlockState(spot.position());
        if (!state.canBeReplaced()) return false;
        return !(spot.item() instanceof BlockItem block) || spot.item().equals(Items.COBWEB)
                || client.level.isUnobstructed(block.getBlock().defaultBlockState(), spot.position(), CollisionContext.empty());
    }

    private Target placement(Minecraft client, Spot spot, Player player, Vec3 eyes) {
        double reach = client.player.blockInteractionRange();
        BlockPos pos = spot.position();
        for (var direction : Direction.values()) {
            var support = pos.relative(direction);
            var state = client.level.getBlockState(support);
            if (state.canBeReplaced() || state.getShape(client.level, support).isEmpty()) continue;
            var aim = Vec3.atCenterOf(pos).add(direction.getStepX() * 0.5, direction.getStepY() * 0.5, direction.getStepZ() * 0.5);
            if (eyes.distanceTo(aim) > reach) continue;
            var hit = trace(client, eyes, aim.subtract(eyes).normalize(), support);
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                    && hit.getBlockPos().relative(hit.getDirection()).equals(pos)) {
                return new Target(pos.immutable(), hit, hit.getLocation(), spot.item(), player);
            }
        }
        return null;
    }

    private boolean validTarget(Minecraft client, Target target) {
        var player = target.player();
        if (player == null || !usable(client, player)) return false;
        if (!inReach(client, player)) return false;
        if (!needed(client, new Spot(target.position(), target.item()))) return false;
        var eyes = client.player.getEyePosition();
        return matches(target, trace(client, eyes, RotationUtils.lookAt(eyes, target.aim()).direction(), target.hit().getBlockPos()));
    }

    private BlockHitResult trace(Minecraft client, Vec3 eyes, Vec3 direction, BlockPos support) {
        double reach = client.player.blockInteractionRange();
        Vec3 end = eyes.add(direction.scale(reach));
        if (raycast.target()) return RotationRaycast.block(eyes, end, support);
        return client.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
    }

    private boolean matches(Target target, BlockHitResult hit) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(target.hit().getBlockPos())
                && hit.getDirection() == target.hit().getDirection();
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 210)
    private void onPreTick(PreTickEvent event) {
        tickSwap(event.client());
    }

    @EventHandler(priority = 30)
    private void onManualUse(ManualUseEvent event) {
        if (!allowManualUse(event.client())) event.cancel();
    }

    @EventHandler(priority = 30)
    private void onTickBoundary(TickBoundaryEvent event) {
        afterTickBoundary();
    }

    @EventHandler(priority = 70)
    private void onInput(InputEvent event) {
        event.input(prepareInput(event.client(), event.input()));
    }
}
