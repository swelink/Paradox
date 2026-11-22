package cc.swelink.systems.modules.render;

import cc.swelink.renderer.CombatLagRenderer;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.settings.MillisecondsSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public final class PlaceModule extends Module {
    private static final int MAX_BOXES = 64;
    private static final float FILL_ALPHA = 0.18f;
    private final MillisecondsSetting duration = addSetting(new MillisecondsSetting("Duration", 500, 100, 2000));
    private final List<Placed> placed = new ArrayList<>();
    private Block pendingBlock;
    private BlockPos[] candidates;
    private BlockState[] before;

    public PlaceModule() {
        super("Place", "Highlights placed blocks", Category.RENDER);
    }

    public void beforeUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        pendingBlock = null;
        if (!isEnabled() || !(player.getItemInHand(hand).getItem() instanceof BlockItem item)) return;
        var level = player.level();
        var clicked = hit.getBlockPos();
        candidates = new BlockPos[] {clicked, clicked.relative(hit.getDirection())};
        before = new BlockState[] {level.getBlockState(candidates[0]), level.getBlockState(candidates[1])};
        pendingBlock = item.getBlock();
    }

    public void afterUse(LocalPlayer player, InteractionResult result) {
        Block block = pendingBlock;
        pendingBlock = null;
        if (block == null || !isEnabled() || !result.consumesAction()) return;
        var level = player.level();
        for (int index = 0; index < candidates.length; index++) {
            var state = level.getBlockState(candidates[index]);
            if (state == before[index] || state.isAir() || !state.is(block)) continue;
            var shape = state.getShape(level, candidates[index]);
            AABB bounds = shape.isEmpty() ? new AABB(candidates[index]) : shape.bounds().move(candidates[index]);
            if (placed.size() >= MAX_BOXES) placed.removeFirst();
            placed.add(new Placed(bounds, System.nanoTime()));
            return;
        }
    }

    public void submit(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector) {
        if (placed.isEmpty() || state.cameraRenderState.isPanoramicMode) return;
        long now = System.nanoTime();
        float lifetime = duration.get() / 1000f;
        placed.removeIf(box -> (now - box.time) * 1.0e-9f >= lifetime);
        int accent = ThemeColors.primary();
        for (var box : placed) {
            float age = Math.clamp((now - box.time) * 1.0e-9f / lifetime, 0, 1);
            float fade = 1 - FrameClock.smoothStep(age);
            CombatLagRenderer.drawBox(box.bounds, ARGB.multiplyAlpha(accent, FILL_ALPHA * fade),
                    pose, state, collector);
        }
    }

    @Override
    protected void onDisable() {
        placed.clear();
        pendingBlock = null;
    }

    private record Placed(AABB bounds, long time) {
    }
}
