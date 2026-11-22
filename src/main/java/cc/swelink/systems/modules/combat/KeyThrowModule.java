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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;

abstract class KeyThrowModule extends Module {
    private final IntSetting throwDelay = addSetting(new IntSetting("Throw delay", 1, 0, 20));
    private final IntSetting switchBackDelay = addSetting(new IntSetting("Swap back delay", 1, 1, 20));
    private final Item item;
    private LocalPlayer player;
    private ClientLevel level;
    private int originalSlot = -1;
    private int selectedSlot = -1;
    private boolean thrown;
    private int readyTick;

    protected KeyThrowModule(String name, String description, Item item) {
        super(name, description, Category.PLAYER);
        this.item = item;
    }

    @Override
    protected void onEnable() {
        var client = Minecraft.getInstance();
        player = client.player;
        level = client.level;
        originalSlot = selectedSlot = -1;
        thrown = false;
        if (!valid(client)) {
            setEnabled(false);
            return;
        }
        var inventory = player.getInventory();
        int slot = player.getMainHandItem().is(item) ? inventory.getSelectedSlot()
                : InventoryUtils.findHotbar(inventory, item);
        if (slot < 0 || player.getCooldowns().isOnCooldown(inventory.getItem(slot))
                || !SlotSelection.begin(this, inventory)) {
            setEnabled(false);
            return;
        }
        originalSlot = inventory.getSelectedSlot();
        selectedSlot = slot;
        if (!SlotSelection.select(this, inventory, selectedSlot)) {
            setEnabled(false);
            return;
        }
        SlotSelection.syncCarriedItem(client);
        readyTick = player.tickCount + throwDelay.get();
    }

    public void beforeTick(Minecraft client) {
        if (isEnabled() && (!valid(client) || !ownsSlot())) setEnabled(false);
    }

    @Override
    public void tickInput(Minecraft client) {
        if (!valid(client) || !ownsSlot()) {
            setEnabled(false);
            return;
        }
        if (player.tickCount - readyTick < 0) return;
        if (thrown) {
            setEnabled(false);
            return;
        }
        var stack = player.getMainHandItem();
        if (!stack.is(item) || player.getCooldowns().isOnCooldown(stack)) {
            setEnabled(false);
            return;
        }
        Paradox.ROTATIONS.holdLocalRotation(client, false);
        var animation = stack.getInteractAnimation();
        var result = client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        if (!(result instanceof InteractionResult.Success success)) {
            setEnabled(false);
            return;
        }
        thrown = true;
        readyTick = player.tickCount + Math.max(1, switchBackDelay.get());
        if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            player.swing(InteractionHand.MAIN_HAND, animation, false);
        }
    }

    private boolean valid(Minecraft client) {
        return player != null && client.player == player && level != null && client.level == level
                && client.gameMode != null && player.isAlive() && !player.isSpectator()
                && !player.isUsingItem() && !player.isHandsBusy() && !player.isPassenger()
                && Gameplay.focused(client)
                && player.containerMenu == player.inventoryMenu;
    }

    private boolean ownsSlot() {
        return player != null && SlotSelection.owns(this, player.getInventory())
                && player.getInventory().getSelectedSlot() == selectedSlot;
    }

    @Override
    protected void onDisable() {
        var client = Minecraft.getInstance();
        boolean current = player != null && client.player == player && client.level == level;
        boolean owned = current && SlotSelection.owns(this, player.getInventory());
        boolean restore = owned && player.getInventory().getSelectedSlot() == selectedSlot;
        SlotSelection.complete(this, restore ? originalSlot : -1);
        if (restore) SlotSelection.syncCarriedItem(client);
        player = null;
        level = null;
        originalSlot = selectedSlot = -1;
        thrown = false;
        readyTick = 0;
    }

    @EventHandler(priority = 80)
    private void onPreTick(PreTickEvent event) {
        beforeTick(event.client());
    }
}
