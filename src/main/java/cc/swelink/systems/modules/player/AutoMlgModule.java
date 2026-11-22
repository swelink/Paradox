package cc.swelink.systems.modules.player;

import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.settings.HeartsSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.FallPrediction;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.world.BlockInteractionController;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoMlgModule extends Module {
    private static final int AIM_LEAD_TICKS = 8;
    private static final int ROTATION_PRIORITY = 10;
    private static final int PICKUP_TIMEOUT_TICKS = 60;
    private final HeartsSetting maxDamage = addSetting(new HeartsSetting("Max damage", 10, 0, 40));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private BlockPos placedWater;
    private int placedAt;
    private boolean placing;
    private final BlockInteractionController<Target> interaction = new BlockInteractionController<>(
            this, this::isEnabled, this::addSetting, true) {
        @Override
        protected boolean keepAimingBetweenInteractions() {
            return true;
        }

        @Override
        protected int rotationPriority() {
            return ROTATION_PRIORITY;
        }

        @Override
        protected boolean restoreAfterUse() {
            return false;
        }

        @Override
        protected Target findTarget(Minecraft client) {
            return AutoMlgModule.this.findTarget(client);
        }

        @Override
        protected boolean validTarget(Minecraft client, Target target) {
            return true;
        }

        @Override
        protected boolean matches(Target target, BlockHitResult hit) {
            return AutoMlgModule.this.matches(target, hit);
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
            return RotationRaycast.blocks(rotation, client.player.blockInteractionRange(),
                    target.item() == Items.BUCKET ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE,
                    only(target.hit().getBlockPos()));
        }

        @Override
        protected InteractionResult interact(Minecraft client, Target target, InteractionHand hand,
                                             BlockHitResult hit, Rotation rotation) {
            placing = target.item() == Items.WATER_BUCKET;
            try {
                return useHeldItem(client, hand, rotation);
            } finally {
                placing = false;
            }
        }
    };

    private record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item) implements BlockInteractionController.BlockTarget {
    }

    public AutoMlgModule() {
        super("AutoMLG", "Water buckets before fall damage", Category.PLAYER);
    }

    @Override
    public void tick(Minecraft client) {
        interaction.tick(client);
    }

    @Override
    public void tickInput(Minecraft client) {
        interaction.tickInput(client);
    }

    @Override
    protected void onDisable() {
        placedWater = null;
        interaction.disable();
    }

    public void recordPlacedWater(BlockPos position) {
        if (placing) {
            placedWater = position.immutable();
            placedAt = Minecraft.getInstance().player.tickCount;
        }
    }

    private BlockPos only(BlockPos pos) {
        return raycast.target() ? pos : null;
    }

    private Target findTarget(Minecraft client) {
        var player = client.player;
        if (placedWater != null) {
            var pickup = pickupTarget(client);
            if (pickup != null) return pickup;
        }
        if (player.getDeltaMovement().y >= 0 || !FallPrediction.airborne(player)
                || InventoryUtils.findHotbar(player.getInventory(), Items.WATER_BUCKET) < 0) {
            return null;
        }
        var landing = FallPrediction.predict(client.level, player, AIM_LEAD_TICKS);
        if (landing.kind() != FallPrediction.Kind.GROUND || !FallPrediction.exceeds(client, landing.fall(), maxDamage.get())) {
            return null;
        }
        var position = landing.position();
        var aim = new Vec3(position.getX() + 0.5, landing.top(), position.getZ() + 0.5);
        return new Target(position, new BlockHitResult(aim, Direction.UP, position, false), aim, Items.WATER_BUCKET);
    }

    private Target pickupTarget(Minecraft client) {
        var player = client.player;
        var fluid = client.level.getFluidState(placedWater);
        boolean expired = player.tickCount - placedAt > PICKUP_TIMEOUT_TICKS;
        if (expired || !fluid.isSource() || !fluid.is(FluidTags.WATER)
                || InventoryUtils.findHotbar(player.getInventory(), Items.BUCKET) < 0) {
            placedWater = null;
            return null;
        }
        var aim = Vec3.atLowerCornerOf(placedWater).add(0.5, fluid.getHeight(client.level, placedWater) * 0.5, 0.5);
        return new Target(placedWater, new BlockHitResult(aim, Direction.UP, placedWater, false), aim, Items.BUCKET);
    }

    private boolean matches(Target target, BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        var pos = hit.getBlockPos();
        var goal = target.position();
        if (target.item() == Items.BUCKET) {
            var player = Minecraft.getInstance().player;
            return pos.equals(goal) && player.onGround() && player.fallDistance == 0;
        }
        return pos.getX() == goal.getX() && pos.getZ() == goal.getZ() && pos.getY() >= goal.getY();
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 195)
    private void onPreTick(PreTickEvent event) {
        interaction.tickSwap(event.client());
    }

    @EventHandler(priority = 10)
    private void onInput(InputEvent event) {
        if (placedWater != null || !interaction.hasTarget()) return;
        var input = event.input();
        event.input(new Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), true, input.sprint()));
    }

    @EventHandler(priority = 10)
    private void onManualUse(ManualUseEvent event) {
        if (!interaction.allowManualUse(event.client())) event.cancel();
    }

    @EventHandler(priority = 10)
    private void onTickBoundary(TickBoundaryEvent event) {
        interaction.afterTickBoundary();
    }
}
