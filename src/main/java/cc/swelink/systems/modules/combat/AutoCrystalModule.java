package cc.swelink.systems.modules.combat;

import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.AttackUtils;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.rotation.RotationRaycast;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class AutoCrystalModule extends Module {
    private final IntSetting switchDelay = addSetting(new IntSetting("Switch delay", 1, 0, 20));
    private final IntSetting placeDelay = addSetting(new IntSetting("Place delay", 1, 1, 20));
    private final IntSetting breakDelay = addSetting(new IntSetting("Break delay", 1, 1, 20));
    private final BooleanSetting obby = addSetting(new BooleanSetting("Obby", false));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private long ticks;
    private long switchTick;
    private long nextPlace;
    private long nextBreak;
    private int selectedSlot = -1;
    private boolean pendingObby;

    public AutoCrystalModule() {
        super("AutoCrystal", "Places and breaks crystals", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        ticks = 0;
        switchTick = 0;
        nextPlace = 0;
        nextBreak = 0;
        selectedSlot = -1;
        pendingObby = obby.get();
    }

    @Override
    public void tickInput(Minecraft client) {
        var player = client.player;
        if (!Gameplay.canAct(client) || client.gameMode == null || player.isUsingItem()
                || SlotSelection.isOwned()) {
            return;
        }
        ticks++;

        int currentSlot = player.getInventory().getSelectedSlot();
        if (selectedSlot != currentSlot) {
            switchTick = selectedSlot < 0 ? ticks - switchDelay.get() : ticks;
            selectedSlot = currentSlot;
        }

        HitResult inputHit = raycast.target() ? targetHit(client) : client.hitResult;
        if (inputHit instanceof EntityHitResult hit && hit.getEntity() instanceof EndCrystal crystal) {
            if (ticks >= nextBreak && crystal.isAlive() && AttackUtils.isInRange(player, hit)) {
                var stack = player.getMainHandItem();
                if (player.isHandsBusy() || !stack.isItemEnabled(client.level.enabledFeatures())
                        || player.cannotAttackWithItem(stack, 0)) {
                    return;
                }
                if (AttackUtils.attack(client, crystal)) {
                    nextBreak = ticks + breakDelay.get();
                }
            }
            return;
        }
        if (!(inputHit instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !player.isWithinBlockInteractionRange(hit.getBlockPos(), 0)) {
            return;
        }

        if (CrystalAuraModule.base(client.level.getBlockState(hit.getBlockPos()))) {
            pendingObby = false;
            if (ticks < nextPlace || !canPlaceCrystal(client, hit.getBlockPos())) {
                return;
            }
            InteractionHand hand = prepareItem(client, Items.END_CRYSTAL);
            if (hand != null) {
                place(client, hit, hand);
            }
        } else if (pendingObby && obby.get() && ticks >= nextPlace) {
            placeObsidian(client, hit);
        }
    }

    private static HitResult targetHit(Minecraft client) {
        var player = client.player;
        var rotation = RotationRaycast.interactionRotation(client, 1);
        HitResult crystal = RotationRaycast.entities(rotation, player.entityInteractionRange(),
                entity -> entity instanceof EndCrystal && entity.isAlive());
        if (crystal != null) {
            return crystal;
        }
        HitResult base = RotationRaycast.firstBlock(rotation, player.blockInteractionRange(),
                CrystalAuraModule::base);
        return base != null ? base : client.hitResult;
    }

    static boolean canPlaceCrystal(Minecraft client, BlockPos base) {
        var above = base.above();
        if (!client.level.isEmptyBlock(above)) {
            return false;
        }
        var space = new AABB(above).expandTowards(0, 1, 0);
        return client.level.getEntities(null, space).isEmpty();
    }

    private void placeObsidian(Minecraft client, BlockHitResult hit) {
        var clicked = client.level.getBlockState(hit.getBlockPos());
        var position = clicked.canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        if (!client.level.getBlockState(position).canBeReplaced()) {
            return;
        }
        if (!hasItem(client, Items.OBSIDIAN)) {
            pendingObby = false;
            return;
        }
        InteractionHand hand = prepareItem(client, Items.OBSIDIAN);
        if (hand == null) {
            return;
        }

        pendingObby = false;
        place(client, hit, hand);
    }

    private void place(Minecraft client, BlockHitResult hit, InteractionHand hand) {
        var stack = client.player.getItemInHand(hand);
        int count = stack.getCount();
        var animation = stack.getInteractAnimation();
        var result = client.gameMode.useItemOn(client.player, hand, hit);
        if (result instanceof InteractionResult.Success success
                && success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            client.player.swing(hand, animation, false);
            if (!stack.isEmpty() && (stack.getCount() != count || client.player.hasInfiniteMaterials())) {
                client.player.itemUsed(hand);
            }
        }
        nextPlace = ticks + placeDelay.get();
    }

    private InteractionHand prepareItem(Minecraft client, Item item) {
        var player = client.player;
        if (!player.getMainHandItem().is(item)) {
            if (player.getOffhandItem().is(item)) {
                return InteractionHand.OFF_HAND;
            }
            int slot = InventoryUtils.findHotbar(client.player.getInventory(), item);
            if (slot < 0) {
                return null;
            }
            player.getInventory().setSelectedSlot(slot);
            SlotSelection.syncCarriedItem(client);
            selectedSlot = slot;
            switchTick = ticks;
        }
        return ticks - switchTick >= switchDelay.get() ? InteractionHand.MAIN_HAND : null;
    }

    private static boolean hasItem(Minecraft client, Item item) {
        return client.player.getOffhandItem().is(item) || InventoryUtils.findHotbar(client.player.getInventory(), item) >= 0;
    }

}
