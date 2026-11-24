package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;

import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class AutoAnchorModule extends Module {
    private final IntSetting placeDelay = addSetting(new IntSetting("Place delay", 1, 1, 20));
    private final IntSetting switchDelay = addSetting(new IntSetting("Switch delay", 1, 0, 20));
    private final IntSetting explodeSlot = addSetting(new IntSetting("Explode slot", 1, 1, 9).bounded());
    private ClientLevel level;
    private BlockPos anchor;
    private boolean waitingForRemoval;
    private long detonatedAt;
    private long ticks;
    private long switchTick;
    private long nextInteraction;
    private int selectedSlot = -1;
    private LocalPlayer returnPlayer;
    private ClientLevel returnLevel;
    private int returnFromSlot = -1;
    private int returnTicks;

    public AutoAnchorModule() {
        super("AutoAnchor", "Explodes anchors you are looking at", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        var client = Minecraft.getInstance();
        clearExplodeReturn();
        level = client.level;
        resetDelays();
        anchor = null;
        waitingForRemoval = false;
    }

    @Override
    protected void onDisable() {
        var client = Minecraft.getInstance();
        if (client.player != null && client.level == level && client.player.isAlive()
                && SlotSelection.owns(this, client.player.getInventory())) {
            returnPlayer = client.player;
            returnLevel = client.level;
            returnFromSlot = client.player.getInventory().getSelectedSlot();
            returnTicks = switchDelay.get();
        }
        SlotSelection.complete(this, -1);
        anchor = null;
        waitingForRemoval = false;
        if (returnPlayer != null && returnTicks == 0) tickExplodeReturn(client);
    }

    public void tickExplodeReturn(Minecraft client) {
        if (returnPlayer == null) return;
        if (client.player != returnPlayer || client.level != returnLevel || !returnPlayer.isAlive()
                || returnPlayer.getInventory().getSelectedSlot() != returnFromSlot
                || !Gameplay.focused(client)) {
            clearExplodeReturn();
            return;
        }
        if (returnTicks > 0 && --returnTicks > 0) return;
        var inventory = returnPlayer.getInventory();
        int slot = explodeSlot.get() - 1;
        if (inventory.getSelectedSlot() == slot) {
            clearExplodeReturn();
            return;
        }
        if (!SlotSelection.begin(this, inventory)) return;
        SlotSelection.select(this, inventory, slot);
        SlotSelection.syncCarriedItem(client);
        SlotSelection.complete(this, -1);
        clearExplodeReturn();
    }

    private void clearExplodeReturn() {
        returnPlayer = null;
        returnLevel = null;
        returnFromSlot = -1;
        returnTicks = 0;
    }

    public void cancelForScreen() {
        SlotSelection.complete(this, -1);
        anchor = null;
        waitingForRemoval = false;
        clearExplodeReturn();
    }

    @Override
    public void tickInput(Minecraft client) {
        var player = client.player;
        if (!canInteract(client)) return;
        if (level != client.level) {
            SlotSelection.complete(this, -1);
            level = client.level;
            resetDelays();
            anchor = null;
            waitingForRemoval = false;
        }
        ticks++;
        var safeAnchor = Paradox.MODULES.get(SafeAnchorModule.class);
        if (safeAnchor.busy(client)) {
            SlotSelection.complete(this, -1);
            return;
        }
        if (waitingForRemoval) {
            if (level.getBlockState(anchor).is(Blocks.RESPAWN_ANCHOR) && ticks - detonatedAt < 10) return;
            anchor = null;
            waitingForRemoval = false;
            SlotSelection.complete(this, -1);
        }
        if (!act(client, player, safeAnchor) && !waitingForRemoval) SlotSelection.complete(this, -1);
    }

    private boolean act(Minecraft client, LocalPlayer player, SafeAnchorModule safeAnchor) {
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !player.isWithinBlockInteractionRange(hit.getBlockPos(), 0)) return false;
        var state = level.getBlockState(hit.getBlockPos());
        if (!state.is(Blocks.RESPAWN_ANCHOR)
                || level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, hit.getBlockPos())) return false;
        int detonateSlot = explodeSlot.get() - 1;
        if (player.getInventory().getItem(detonateSlot).is(Items.GLOWSTONE)) return false;
        if (state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
            int slot = InventoryUtils.findHotbar(client.player.getInventory(), Items.GLOWSTONE);
            if (slot < 0) return false;
            if (prepareSlot(client, slot)) interact(client, hit);
            return true;
        }
        if (player.getOffhandItem().is(Items.GLOWSTONE)
                && state.getValue(RespawnAnchorBlock.CHARGE) < RespawnAnchorBlock.MAX_CHARGES) return false;
        if (!safeAnchor.allowsDetonation(client, hit.getBlockPos())) return false;
        if (prepareSlot(client, detonateSlot) && interact(client, hit)) {
            anchor = hit.getBlockPos().immutable();
            waitingForRemoval = true;
            detonatedAt = ticks;
        }
        return true;
    }

    private static boolean canInteract(Minecraft client) {
        var player = client.player;
        return Gameplay.canAct(client) && client.gameMode != null && !player.isUsingItem();
    }

    private boolean prepareSlot(Minecraft client, int slot) {
        var inventory = client.player.getInventory();
        if (!SlotSelection.begin(this, inventory)) {
            return false;
        }
        int currentSlot = inventory.getSelectedSlot();
        if (selectedSlot != currentSlot) {
            switchTick = selectedSlot < 0 ? ticks - switchDelay.get() : ticks;
            selectedSlot = currentSlot;
        }
        if (currentSlot != slot) {
            SlotSelection.select(this, inventory, slot);
            SlotSelection.syncCarriedItem(client);
            selectedSlot = slot;
            switchTick = ticks;
        }
        return ticks - switchTick >= switchDelay.get();
    }

    private boolean interact(Minecraft client, BlockHitResult hit) {
        if (ticks < nextInteraction) {
            return false;
        }
        var hand = InteractionHand.MAIN_HAND;
        var stack = client.player.getItemInHand(hand);
        int count = stack.getCount();
        var animation = stack.getInteractAnimation();
        var result = client.gameMode.useItemOn(client.player, hand, hit);
        nextInteraction = ticks + placeDelay.get();
        if (!(result instanceof InteractionResult.Success success)) return false;
        if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            client.player.swing(hand, animation, false);
            if (!stack.isEmpty() && (stack.getCount() != count || client.player.hasInfiniteMaterials())) {
                client.player.itemUsed(hand);
            }
        }
        return true;
    }

    private void resetDelays() {
        ticks = 0;
        switchTick = 0;
        nextInteraction = 0;
        selectedSlot = -1;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 60)
    private void onPreTick(PreTickEvent event) {
        if (Gameplay.inMenu(event.client())) cancelForScreen();
    }

    @EventHandler(priority = 150)
    private void onPreTick2(PreTickEvent event) {
        tickExplodeReturn(event.client());
    }
}
