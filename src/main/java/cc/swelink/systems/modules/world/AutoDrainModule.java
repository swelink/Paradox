package cc.swelink.systems.modules.world;

import cc.swelink.events.EventHandler;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationUtils;
import cc.swelink.utils.world.BlockInteractionController;
import cc.swelink.utils.world.PlacedFluidTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoDrainModule extends Module {
    private final BooleanSetting ignoreSelf = addSetting(new BooleanSetting("Ignore self", true));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final PlacedFluidTracker placedFluids = new PlacedFluidTracker();
    private final BlockInteractionController<Target> interaction = new BlockInteractionController<>(
            this, this::isEnabled, this::addSetting, false) {
        @Override
        protected boolean keepAimingBetweenInteractions() {
            return true;
        }

        @Override
        protected Target findTarget(Minecraft client) {
            return AutoDrainModule.this.findTarget(client);
        }

        @Override
        protected boolean validTarget(Minecraft client, Target target) {
            return AutoDrainModule.this.validTarget(client, target);
        }

        @Override
        protected boolean matches(Target target, BlockHitResult hit) {
            return AutoDrainModule.this.matches(target, hit);
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
            return AutoDrainModule.this.traceTarget(client, target, rotation);
        }

        @Override
        protected InteractionResult interact(Minecraft client, Target target, InteractionHand hand,
                                             BlockHitResult hit, Rotation rotation) {
            return target.item() == Items.BUCKET ? useHeldItem(client, hand, rotation)
                    : super.interact(client, target, hand, hit, rotation);
        }
    };

    private record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item) implements BlockInteractionController.BlockTarget {
    }

    public AutoDrainModule() {
        super("AutoDrain", "Picks up water and lava", Category.PLAYER);
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

    @Override
    protected void onDisable() {
        interaction.disable();
    }

    public void tickSwap(Minecraft client) {
        placedFluids.update(client.level);
        interaction.tickSwap(client);
    }

    public void recordPlacedLiquid(Minecraft client, BlockPos position) {
        placedFluids.record(client.level, position);
    }

    private BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
        return RotationRaycast.blocks(rotation, client.player.blockInteractionRange(),
                target.item() == Items.BUCKET ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE,
                only(target.hit().getBlockPos()));
    }

    private BlockPos only(BlockPos pos) {
        return raycast.target() ? pos : null;
    }

    private Target findTarget(Minecraft client) {
        boolean bucket = InventoryUtils.findHotbar(client.player.getInventory(), Items.BUCKET) >= 0;
        boolean web = InventoryUtils.findHotbar(client.player.getInventory(), Items.COBWEB) >= 0;
        if (!bucket && !web) {
            return null;
        }
        var eyes = client.player.getEyePosition();
        var camera = new Rotation(client.player.getYRot(), client.player.getXRot());
        var center = BlockPos.containing(eyes);
        int radius = (int) Math.ceil(client.player.blockInteractionRange());
        Target closest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (!client.player.isWithinBlockInteractionRange(pos, 0)) {
                continue;
            }
            Target candidate = null;
            if (bucket && validLiquid(client, pos, Items.BUCKET)) {
                var fluid = client.level.getFluidState(pos);
                var aim = Vec3.atLowerCornerOf(pos).add(0.5, fluid.getHeight(client.level, pos) * 0.5, 0.5);
                var rotation = RotationUtils.lookAt(eyes, aim);
                var hit = RotationRaycast.blocks(rotation, client.player.blockInteractionRange(), ClipContext.Fluid.SOURCE_ONLY, only(pos));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                    candidate = new Target(pos.immutable(), hit, aim, Items.BUCKET);
                }
            }
            if (candidate == null && web && validLiquid(client, pos, Items.COBWEB)) {
                candidate = webTarget(client, pos);
            }
            if (candidate != null && matches(candidate, traceTarget(client, candidate, camera))) {
                return candidate;
            }
            if (candidate != null && eyes.distanceToSqr(candidate.aim()) < distance) {
                closest = candidate;
                distance = eyes.distanceToSqr(candidate.aim());
            }
        }
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

    private boolean validLiquid(Minecraft client, BlockPos pos, Item item) {
        if (ignoreSelf.get() && placedFluids.contains(pos)) {
            return false;
        }
        var state = client.level.getBlockState(pos);
        var fluid = state.getFluidState();
        if (!fluid.isSource() || (!fluid.is(FluidTags.WATER) && !fluid.is(FluidTags.LAVA))) {
            return false;
        }
        return item == Items.BUCKET ? state.getBlock() instanceof BucketPickup
                : state.getBlock() instanceof LiquidBlock && state.canBeReplaced();
    }

    private boolean validTarget(Minecraft client, Target target) {
        return validLiquid(client, target.position(), target.item()) && visible(client, target);
    }

    private boolean matches(Target target, BlockHitResult hit) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK
                && (target.item() == Items.BUCKET ? hit.getBlockPos().equals(target.position())
                : hit.getBlockPos().equals(target.hit().getBlockPos())
                && hit.getDirection() == target.hit().getDirection());
    }

    private boolean visible(Minecraft client, Target target) {
        return matches(target, RotationRaycast.blocks(RotationUtils.lookAt(client.player.getEyePosition(), target.aim()),
                client.player.blockInteractionRange(),
                target.item() == Items.BUCKET ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE,
                only(target.hit().getBlockPos())));
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 190)
    private void onPreTick(PreTickEvent event) {
        tickSwap(event.client());
    }

    @EventHandler(priority = 10)
    private void onManualUse(ManualUseEvent event) {
        if (!allowManualUse(event.client())) event.cancel();
    }

    @EventHandler(priority = 10)
    private void onTickBoundary(TickBoundaryEvent event) {
        afterTickBoundary();
    }
}
