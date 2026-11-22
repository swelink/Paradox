package cc.swelink.systems.modules.combat;

import cc.swelink.utils.time.GameTime;
import cc.swelink.mixin.AbstractContainerScreenInvoker;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.SlotSelection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

public final class HoverTotemModule extends Module {
    private final BooleanSetting offhand = addSetting(new BooleanSetting("Offhand", true));
    private final IntSetting hotbarSlot = addSetting(new IntSetting("Hotbar slot", 0, 0, 9) {
        @Override
        protected String formatValue(int value) {
            return value == 0 ? "Off" : Integer.toString(value);
        }
    }.bounded());
    private final IntSetting delay = addSetting(new IntSetting("Delay (ms)", 100, 0, 1000));
    private AbstractContainerScreen<?> hoverScreen;
    private Slot hoverSlot;
    private int hoverDestination = -1;
    private long hoverStarted;

    public HoverTotemModule() {
        super("HoverTotem", "totem the hover", Category.COMBAT);
    }

    @Override
    public void tick(Minecraft client) {
        var player = client.player;
        if (player == null || client.gameMode == null || player.isSpectator()
                || !client.getWindow().isFocused() || SlotSelection.isOwned()
                || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)
                || screen.getMenu() != player.containerMenu || !screen.getMenu().getCarried().isEmpty()
                || client.mouseHandler.isLeftPressed() || client.mouseHandler.isRightPressed()) {
            resetHover();
            return;
        }

        var slot = ((AbstractContainerScreenInvoker) screen).paradox$getHoveredSlot(
                client.mouseHandler.getScaledXPos(client.getWindow()),
                client.mouseHandler.getScaledYPos(client.getWindow()));
        var inventory = player.getInventory();
        if (slot == null || slot.container != inventory || slot.getContainerSlot() < 0
                || slot.getContainerSlot() >= Inventory.INVENTORY_SIZE || !slot.isActive()
                || !slot.mayPickup(player) || !slot.getItem().is(Items.TOTEM_OF_UNDYING)) {
            resetHover();
            return;
        }

        int destination = offhand.get() && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                ? Inventory.SLOT_OFFHAND
                : hotbarSlot.get() > 0 && !inventory.getItem(hotbarSlot.get() - 1).is(Items.TOTEM_OF_UNDYING)
                ? hotbarSlot.get() - 1 : -1;
        if (destination < 0 || destination == slot.getContainerSlot()
                || !slot.mayPlace(inventory.getItem(destination))) {
            resetHover();
            return;
        }

        long now = GameTime.nanos();
        if (hoverScreen != screen || hoverSlot != slot || hoverDestination != destination) {
            hoverScreen = screen;
            hoverSlot = slot;
            hoverDestination = destination;
            hoverStarted = now;
        }
        if (now - hoverStarted < delay.get() * 1_000_000L) {
            return;
        }

        client.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, destination,
                ContainerInput.SWAP, player);
        resetHover();
    }

    @Override
    protected void onDisable() {
        resetHover();
    }

    private void resetHover() {
        hoverScreen = null;
        hoverSlot = null;
        hoverDestination = -1;
        hoverStarted = 0;
    }
}
