package cc.swelink.utils.world;

import cc.swelink.utils.rotation.RotationRaycast;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BlockPlacement {
    private static final double[] FACE_POINTS = {0, 0.35, -0.35};

    public record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item)
            implements BlockInteractionController.BlockTarget {
    }

    private BlockPlacement() {
    }

    public static Item placeableItem(Inventory inventory) {
        var held = inventory.getSelectedItem();
        if (placeable(held)) return held.getItem();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (placeable(stack)) return stack.getItem();
        }
        return null;
    }

    public static boolean placeable(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return false;
        var block = item.getBlock();
        return !(block instanceof FallingBlock) && !(block instanceof EntityBlock)
                && block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static Target find(Minecraft client, BlockPos pos, Item item, Vec3 eyes, boolean target) {
        double reach = client.player.blockInteractionRange();
        for (var direction : Direction.values()) {
            var support = pos.relative(direction);
            var state = client.level.getBlockState(support);
            if (!supports(client, support, state)) continue;
            var center = Vec3.atCenterOf(pos).add(direction.getStepX() * 0.5, direction.getStepY() * 0.5, direction.getStepZ() * 0.5);
            var axis = direction.getAxis();
            for (double first : FACE_POINTS) {
                for (double second : FACE_POINTS) {
                    var aim = center.add(offset(axis, first, second));
                    if (eyes.distanceTo(aim) > reach) continue;
                    var hit = trace(client, eyes, aim.subtract(eyes).normalize(), target ? support : null);
                    if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                            && hit.getBlockPos().relative(hit.getDirection()).equals(pos)) {
                        return new Target(pos, hit, hit.getLocation(), item);
                    }
                }
            }
        }
        return null;
    }

    public static BlockHitResult trace(Minecraft client, Vec3 eyes, Vec3 direction) {
        return trace(client, eyes, direction, null);
    }

    public static BlockHitResult trace(Minecraft client, Vec3 eyes, Vec3 direction, BlockPos only) {
        var end = eyes.add(direction.scale(client.player.blockInteractionRange()));
        if (only != null) return RotationRaycast.block(eyes, end, only);
        return client.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
    }

    public static boolean matches(Target target, BlockHitResult hit) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(target.hit().getBlockPos())
                && hit.getDirection() == target.hit().getDirection();
    }

    private static boolean supports(Minecraft client, BlockPos pos, BlockState state) {
        return !state.canBeReplaced() && !state.hasBlockEntity() && !state.getShape(client.level, pos).isEmpty();
    }

    private static Vec3 offset(Direction.Axis axis, double first, double second) {
        return switch (axis) {
            case X -> new Vec3(0, first, second);
            case Y -> new Vec3(first, 0, second);
            case Z -> new Vec3(first, second, 0);
        };
    }
}
