package cc.swelink.systems.modules.player;

import cc.swelink.events.EventHandler;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.settings.HeartsSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.FallPrediction;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.world.BlockInteractionController;
import cc.swelink.utils.world.BlockPlacement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Comparator;

public final class ClutchModule extends Module {
    private static final int PREDICTED_TICKS = 40;
    private static final int AIM_AHEAD_TICKS = 2;
    private static final int SEARCH_RADIUS = 3;
    private static final int SEARCH_DEPTH = 5;
    private static final int ROTATION_PRIORITY = 9;
    private static final float VERTICAL_WEIGHT = 0.25f;
    private static final double MIN_DROP = 0.6;
    private final HeartsSetting maxDamage = addSetting(new HeartsSetting("Max damage", 0, 0, 40));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final BlockInteractionController<BlockPlacement.Target> interaction = new BlockInteractionController<>(
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
        protected BlockPlacement.Target findTarget(Minecraft client) {
            return ClutchModule.this.findTarget(client);
        }

        @Override
        protected boolean validTarget(Minecraft client, BlockPlacement.Target target) {
            return true;
        }

        @Override
        protected boolean matches(BlockPlacement.Target target, BlockHitResult hit) {
            return BlockPlacement.matches(target, hit);
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, BlockPlacement.Target target, Rotation rotation) {
            return BlockPlacement.trace(client, client.player.getEyePosition(), rotation.direction(),
                    only(target.hit().getBlockPos()));
        }
    };

    private double groundY = Double.NaN;

    private record Spot(BlockPos position, double score) {
    }

    public ClutchModule() {
        super("Clutch", "Places blocks to save falls", Category.PLAYER);
        interaction.setDefaultDelays(0, 0);
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
        interaction.disable();
    }

    private BlockPos only(BlockPos pos) {
        return raycast.target() ? pos : null;
    }

    private BlockPlacement.Target findTarget(Minecraft client) {
        var player = client.player;
        if (!FallPrediction.airborne(player)) {
            groundY = player.getY();
            return null;
        }
        if (!unsafe(client)) return null;
        var inventory = player.getInventory();
        var item = BlockPlacement.placeableItem(inventory);
        if (item == null) return null;
        var state = ((BlockItem) item).getBlock().defaultBlockState();
        var eyes = player.getEyePosition();
        var ahead = FallPrediction.positionAfter(player, AIM_AHEAD_TICKS);
        int centerX = Mth.floor(ahead.x);
        int centerZ = Mth.floor(ahead.z);
        int top = Mth.floor(player.getY()) - 1;
        var spots = new ArrayList<Spot>();
        for (int y = top; y > top - SEARCH_DEPTH; y--) {
            for (int x = centerX - SEARCH_RADIUS; x <= centerX + SEARCH_RADIUS; x++) {
                for (int z = centerZ - SEARCH_RADIUS; z <= centerZ + SEARCH_RADIUS; z++) {
                    var pos = new BlockPos(x, y, z);
                    if (!client.level.getBlockState(pos).canBeReplaced()) continue;
                    double dx = x + 0.5 - ahead.x;
                    double dz = z + 0.5 - ahead.z;
                    double dy = y - top;
                    spots.add(new Spot(pos, dx * dx + dz * dz + dy * dy * VERTICAL_WEIGHT));
                }
            }
        }
        spots.sort(Comparator.comparingDouble(Spot::score));
        for (var spot : spots) {
            if (!client.level.isUnobstructed(state, spot.position(), CollisionContext.empty())) continue;
            var target = BlockPlacement.find(client, spot.position(), item, eyes, only(spot.position()) != null);
            if (target != null) return target;
        }
        return null;
    }

    private boolean unsafe(Minecraft client) {
        var prediction = FallPrediction.predict(client.level, client.player, PREDICTED_TICKS);
        return switch (prediction.kind()) {
            case LIQUID -> false;
            case VOID -> true;
            case GROUND -> FallPrediction.exceeds(client, prediction.fall(), maxDamage.get())
                    || maxDamage.get() == 0 && groundY - prediction.top() >= MIN_DROP;
            case UNKNOWN -> FallPrediction.exceeds(client, prediction.fall(), maxDamage.get());
        };
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 194)
    private void onPreTick(PreTickEvent event) {
        interaction.tickSwap(event.client());
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
