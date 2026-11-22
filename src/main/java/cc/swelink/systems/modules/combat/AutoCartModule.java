package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.mixin.ArrowTrajectoryAccessor;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.projectile.ProjectileTrajectory;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationUtils;
import cc.swelink.utils.world.BlockInteractionController;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class AutoCartModule extends Module {
    private static final List<Item> RAILS = List.of(Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL);
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final BlockInteractionController<Target> interaction = new BlockInteractionController<>(
            this, this::isEnabled, this::addSetting, true) {
        @Override
        protected Target findTarget(Minecraft client) {
            return AutoCartModule.this.findTarget(client);
        }

        @Override
        protected boolean validTarget(Minecraft client, Target target) {
            return AutoCartModule.this.validTarget(client, target);
        }

        @Override
        protected boolean matches(Target target, BlockHitResult hit) {
            return hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target.hit().getBlockPos())
                    && (target.item() == Items.TNT_MINECART || hit.getDirection() == target.hit().getDirection());
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
            return RotationRaycast.blocks(rotation, client.player.blockInteractionRange(), ClipContext.Fluid.NONE,
                    only(target.hit().getBlockPos()));
        }

        @Override
        protected int rotationPriority() {
            return 6;
        }
    };

    private record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item, AbstractArrow arrow, int ticksLeft)
            implements BlockInteractionController.BlockTarget {
    }

    private record Landing(AbstractArrow arrow, BlockPos spot, double ticks) {
    }

    public record Incoming(AbstractArrow arrow, BlockPos spot) {
    }

    public Incoming incoming(Minecraft client) {
        if (!isEnabled() || client.player == null || client.level == null) return null;
        Landing landing = landing(client);
        return landing == null ? null : new Incoming(landing.arrow(), landing.spot());
    }

    public AutoCartModule() {
        super("AutoCart", "Puts a TNT cart under your arrow", Category.COMBAT);
        interaction.setDefaultDelays(0, 0);
    }

    @Override
    protected void onDisable() {
        interaction.disable();
    }

    public void tickSwap(Minecraft client) {
        interaction.tickSwap(client);
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

    private Target findTarget(Minecraft client) {
        if (InventoryUtils.findHotbar(client.player.getInventory(), Items.TNT_MINECART) < 0) return null;
        Landing landing = landing(client);
        if (landing == null) return null;
        return target(client, landing);
    }

    private Target target(Minecraft client, Landing landing) {
        BlockPos spot = landing.spot();
        var state = client.level.getBlockState(spot);
        if (state.getBlock() instanceof BaseRailBlock) {
            if (landing.ticks() < 1 || hasCart(client, spot)) return null;
            if (Paradox.MODULES.get(SafeCartModule.class).busy(client)) return null;
            Vec3 aim = new Vec3(spot.getX() + 0.5, spot.getY() + 0.05, spot.getZ() + 0.5);
            var hit = RotationRaycast.blocks(RotationUtils.lookAt(client.player.getEyePosition(), aim),
                    client.player.blockInteractionRange(), ClipContext.Fluid.NONE, only(spot));
            if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(spot)) return null;
            return new Target(spot, hit, hit.getLocation(), Items.TNT_MINECART, landing.arrow(), (int) landing.ticks());
        }
        if (landing.ticks() < 2 || !state.canBeReplaced() || !client.level.getEntities(null, new AABB(spot)).isEmpty()) return null;
        Item rail = rail(client);
        if (rail == null) return null;
        BlockPos below = spot.below();
        if (!client.level.getBlockState(below).isFaceSturdy(client.level, below, Direction.UP)) return null;
        Vec3 aim = Vec3.atCenterOf(below).add(0, 0.5, 0);
        var hit = RotationRaycast.blocks(RotationUtils.lookAt(client.player.getEyePosition(), aim),
                client.player.blockInteractionRange(), ClipContext.Fluid.NONE, only(below));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(below)
                || hit.getDirection() != Direction.UP) return null;
        return new Target(spot, hit, hit.getLocation(), rail, landing.arrow(), (int) landing.ticks());
    }

    private BlockPos only(BlockPos pos) {
        return raycast.target() ? pos : null;
    }

    private boolean validTarget(Minecraft client, Target target) {
        if (!target.arrow().isAlive() || ((ArrowTrajectoryAccessor) target.arrow()).paradox$isInGround()) return false;
        Landing landing = predict(client, target.arrow());
        if (landing == null || !landing.spot().equals(target.position())) return false;
        Target fresh = target(client, landing);
        return fresh != null && fresh.item() == target.item();
    }

    private Landing landing(Minecraft client) {
        Landing best = null;
        for (var arrow : client.level.getEntitiesOfClass(AbstractArrow.class, client.player.getBoundingBox().inflate(96),
                arrow -> arrow.getOwner() == client.player && arrow.isOnFire() && arrow.isAlive()
                        && !((ArrowTrajectoryAccessor) arrow).paradox$isInGround() && !arrow.isNoPhysics())) {
            Landing landing = predict(client, arrow);
            if (landing != null && (best == null || landing.ticks() < best.ticks())) best = landing;
        }
        return best;
    }

    private static Landing predict(Minecraft client, AbstractArrow arrow) {
        var level = client.level;
        var path = ProjectileTrajectory.trace(arrow.position(), arrow.getDeltaMovement(), true,
                arrow.isNoGravity() ? 0 : arrow.getGravity(), 200, new ProjectileTrajectory.Environment() {
                    @Override
                    public boolean loaded(Vec3 point) {
                        return point.y >= level.getMinY() - 16 && level.hasChunkAt(BlockPos.containing(point));
                    }

                    @Override
                    public boolean water(Vec3 point) {
                        var block = BlockPos.containing(point);
                        var fluid = level.getFluidState(block);
                        return fluid.is(FluidTags.WATER) && point.y - block.getY() < fluid.getHeight(level, block);
                    }

                    @Override
                    public ProjectileTrajectory.Impact collide(Vec3 from, Vec3 to, int tick) {
                        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow));
                        if (hit.getType() == HitResult.Type.MISS) return null;
                        return new ProjectileTrajectory.Impact(hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()));
                    }
                });
        var impact = path.impact();
        if (impact == null) return null;
        Vec3 normal = impact.normal();
        BlockPos block = BlockPos.containing(impact.position().subtract(normal.scale(0.01)));
        BlockPos spot;
        if (normal.y > 0.5) {
            spot = block.above();
        } else if (Math.abs(normal.y) < 0.5) {
            spot = BlockPos.containing(impact.position().add(normal.scale(0.01)));
        } else {
            return null;
        }
        if (client.level.getBlockState(spot).getBlock() instanceof BaseRailBlock && hasCart(client, spot)) return null;
        return new Landing(arrow, spot.immutable(), path.ticks());
    }

    private static boolean hasCart(Minecraft client, BlockPos spot) {
        return !client.level.getEntitiesOfClass(MinecartTNT.class, new AABB(spot).inflate(0.5)).isEmpty();
    }

    private static Item rail(Minecraft client) {
        for (Item rail : RAILS) {
            if (InventoryUtils.findHotbar(client.player.getInventory(), rail) >= 0) return rail;
        }
        return null;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 220)
    private void onPreTick(PreTickEvent event) {
        tickSwap(event.client());
    }

    @EventHandler(priority = 40)
    private void onManualUse(ManualUseEvent event) {
        if (!allowManualUse(event.client())) event.cancel();
    }

    @EventHandler(priority = 40)
    private void onTickBoundary(TickBoundaryEvent event) {
        afterTickBoundary();
    }
}
