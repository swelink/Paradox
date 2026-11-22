package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.settings.HeartsSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.AnchorDamage;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.projectile.BlockCollisions;
import cc.swelink.utils.projectile.ProjectileTrajectory;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.world.BlockInteractionController;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SafeCartModule extends Module {
    private static final float WORTH_IT = 0.5f;
    private static final double SEARCH = 10;
    private static final double CART_HEIGHT = 0.0625;
    private static final List<Item> PREFERRED = List.of(Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.ENDER_CHEST);
    private final HeartsSetting minDamage = addSetting(new HeartsSetting("Min damage", 4, 0, 40));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final BlockInteractionController<Target> interaction = new BlockInteractionController<>(
            this, this::isEnabled, this::addSetting, true) {
        @Override
        protected Target findTarget(Minecraft client) {
            return SafeCartModule.this.findTarget(client);
        }

        @Override
        protected boolean validTarget(Minecraft client, Target target) {
            Target fresh = SafeCartModule.this.findTarget(client);
            return fresh != null && fresh.position().equals(target.position());
        }

        @Override
        protected boolean matches(Target target, BlockHitResult hit) {
            return hit != null && hit.getType() == HitResult.Type.BLOCK
                    && hit.getBlockPos().equals(target.hit().getBlockPos())
                    && hit.getDirection() == target.hit().getDirection();
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
            return trace(client, client.player.getEyePosition(), rotation.direction(), target.hit().getBlockPos());
        }

        @Override
        protected int rotationPriority() {
            return 7;
        }
    };
    private final Set<BlockPos> shielded = new HashSet<>();
    private Target placing;

    private record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item, BlockPos danger)
            implements BlockInteractionController.BlockTarget {
    }

    private record Danger(BlockPos block, Vec3 center) {
    }

    public SafeCartModule() {
        super("SafeCart", "Blocks your own TNT cart blast", Category.COMBAT);
        interaction.setDefaultDelays(0, 0);
    }

    public boolean busy(Minecraft client) {
        return isEnabled() && findTarget(client) != null;
    }

    private Target findTarget(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null || AnchorDamage.harmless(player)) return null;
        Item item = shieldItem(client);
        if (item == null) return null;
        VoxelShape shape = shape(item);
        var dangers = new ArrayList<Danger>();
        for (var cart : client.level.getEntitiesOfClass(MinecartTNT.class, player.getBoundingBox().inflate(SEARCH))) {
            dangers.add(new Danger(cart.blockPosition(), cart.position()));
        }
        var incoming = Paradox.MODULES.get(AutoCartModule.class).incoming(client);
        List<Vec3> arrowPath = List.of();
        if (incoming != null) {
            BlockPos spot = incoming.spot();
            dangers.add(new Danger(spot, Vec3.atBottomCenterOf(spot).add(0, CART_HEIGHT, 0)));
            var arrow = incoming.arrow();
            arrowPath = ProjectileTrajectory.trace(arrow.position(), arrow.getDeltaMovement(), true,
                    arrow.isNoGravity() ? 0 : arrow.getGravity(), 200, new BlockCollisions(client.level, arrow)).points();
        }
        if (dangers.isEmpty()) shielded.clear();
        Danger danger = null;
        float worst = minDamage.get();
        for (Danger candidate : dangers) {
            if (shielded.contains(candidate.block())) continue;
            float damage = AnchorDamage.cartHealthDamage(client, player, candidate.center(), null, shape);
            if (damage > worst) {
                worst = damage;
                danger = candidate;
            }
        }
        if (danger == null) return null;
        Target best = null;
        float bestDamage = worst - WORTH_IT;
        BlockPos cartBlock = danger.block();
        for (BlockPos candidate : BlockPos.betweenClosed(cartBlock.offset(-1, -1, -1), cartBlock.offset(1, 1, 1))) {
            if (candidate.equals(cartBlock) || crossesPath(candidate, arrowPath)) continue;
            Target target = placement(client, candidate.immutable(), item, cartBlock);
            if (target == null) continue;
            float damage = AnchorDamage.cartHealthDamage(client, player, danger.center(), target.position(), shape);
            if (damage < bestDamage) {
                best = target;
                bestDamage = damage;
            }
        }
        return best;
    }

    private static boolean crossesPath(BlockPos block, List<Vec3> path) {
        var box = new AABB(block).inflate(0.1);
        for (int index = 1; index < path.size(); index++) {
            Vec3 from = path.get(index - 1);
            Vec3 to = path.get(index);
            if (box.contains(from) || box.clip(from, to).isPresent()) return true;
        }
        return false;
    }

    private static Item shieldItem(Minecraft client) {
        var inventory = client.player.getInventory();
        for (Item item : PREFERRED) {
            if (InventoryUtils.findHotbar(inventory, item) >= 0) return item;
        }
        Item partial = null;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!usableBlock(stack)) continue;
            if (shape(stack.getItem()).equals(Shapes.block())) return stack.getItem();
            if (partial == null) partial = stack.getItem();
        }
        return partial;
    }

    private static boolean usableBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        var block = item.getBlock();
        if (block instanceof BaseRailBlock || block instanceof FallingBlock) return false;
        return !shape(item).isEmpty();
    }

    private static VoxelShape shape(Item item) {
        if (!(item instanceof BlockItem blockItem)) return Shapes.empty();
        return blockItem.getBlock().defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private Target placement(Minecraft client, BlockPos spot, Item item, BlockPos danger) {
        var level = client.level;
        BlockState current = level.getBlockState(spot);
        boolean replaceable = current.isAir() || current.getBlock() instanceof BaseFireBlock
                || current.getBlock() instanceof LiquidBlock;
        if (!replaceable || !(item instanceof BlockItem blockItem)
                || !level.isUnobstructed(blockItem.getBlock().defaultBlockState(), spot, CollisionContext.empty())) return null;
        var eyes = client.player.getEyePosition();
        double reach = client.player.blockInteractionRange();
        for (Direction direction : Direction.values()) {
            BlockPos support = spot.relative(direction);
            if (support.equals(danger)) continue;
            BlockState state = level.getBlockState(support);
            if (state.canBeReplaced() || state.getShape(level, support).isEmpty() || SafeAnchorModule.usable(state)) continue;
            Vec3 aim = Vec3.atCenterOf(spot).add(direction.getStepX() * 0.5, direction.getStepY() * 0.5,
                    direction.getStepZ() * 0.5);
            if (eyes.distanceTo(aim) > reach) continue;
            var hit = trace(client, eyes, aim.subtract(eyes).normalize(), support);
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                    && hit.getBlockPos().relative(hit.getDirection()).equals(spot)) {
                return new Target(spot, hit, hit.getLocation(), item, danger);
            }
        }
        return null;
    }

    private BlockHitResult trace(Minecraft client, Vec3 eyes, Vec3 direction, BlockPos target) {
        Vec3 end = eyes.add(direction.scale(client.player.blockInteractionRange()));
        if (raycast.target()) return RotationRaycast.block(eyes, end, target);
        return client.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
    }

    @Override
    public void tick(Minecraft client) {
        interaction.tick(client);
    }

    @Override
    public void tickInput(Minecraft client) {
        if (client.player == null || client.level == null) return;
        Target wanted = findTarget(client);
        if (wanted != null) placing = wanted;
        interaction.tickInput(client);
        if (placing != null && !client.level.getBlockState(placing.position()).canBeReplaced()) {
            shielded.add(placing.danger());
            placing = null;
        }
    }

    @Override
    protected void onDisable() {
        interaction.disable();
        shielded.clear();
        placing = null;
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
        if (!interaction.allowManualUse(event.client())) event.cancel();
    }

    @EventHandler(priority = 36)
    private void onTickBoundary(TickBoundaryEvent event) {
        interaction.afterTickBoundary();
    }
}
