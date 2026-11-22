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
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class PearlCatchModule extends Module {
    private final IntSetting switchDelay = addSetting(new IntSetting("Switch delay", 1, 0, 20));
    private final IntSetting throwDelay = addSetting(new IntSetting("Throw delay", 1, 0, 20));
    private final RotationSettings rotations = new RotationSettings(this::addSetting);
    private LocalPlayer player;
    private ClientLevel level;
    private int originalSlot = -1;
    private int selectedSlot = -1;
    private int ticks;
    private int switchedAt;
    private int readyAt = -1;
    private boolean windCharge;
    private boolean switchPending;
    private boolean finished;
    private float yaw;

    public PearlCatchModule() {
        super("PearlCatch", "Pearls up then wind charges", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        var client = Minecraft.getInstance();
        player = client.player;
        level = client.level;
        originalSlot = selectedSlot = -1;
        ticks = switchedAt = 0;
        readyAt = -1;
        windCharge = switchPending = finished = false;
        if (!ready(client) || InventoryUtils.findHotbar(player.getInventory(), Items.ENDER_PEARL) < 0
                || InventoryUtils.findHotbar(player.getInventory(), Items.WIND_CHARGE) < 0) {
            setEnabled(false);
            return;
        }
        yaw = player.getYRot();
    }

    public void beforeTick(Minecraft client) {
        if (isEnabled() && !valid(client) && !waitingForScreen(client)) setEnabled(false);
    }

    @Override
    public void tick(Minecraft client) {
        if (waitingForScreen(client)) return;
        if (!valid(client) || ++ticks > 2400) {
            setEnabled(false);
            return;
        }
        if (originalSlot >= 0) rotations.request(client, new Rotation(yaw, -90), 10);
    }

    @Override
    public void tickInput(Minecraft client) {
        if (waitingForScreen(client)) return;
        if (!valid(client) || finished) {
            setEnabled(false);
            return;
        }
        var inventory = player.getInventory();
        if (originalSlot < 0) {
            if (!SlotSelection.begin(this, inventory)) return;
            originalSlot = inventory.getSelectedSlot();
            if (!select(client, Items.ENDER_PEARL)) setEnabled(false);
            return;
        }
        if (switchPending) {
            switchPending = false;
            if (!select(client, Items.WIND_CHARGE)) setEnabled(false);
            return;
        }
        if (!SlotSelection.owns(this, inventory) || inventory.getSelectedSlot() != selectedSlot
                || !player.getMainHandItem().is(windCharge ? Items.WIND_CHARGE : Items.ENDER_PEARL)) {
            setEnabled(false);
            return;
        }
        var currentRotation = rotations.current(client);
        var serverRotation = Paradox.ROTATIONS.serverRotation();
        var packetRotation = Paradox.ROTATIONS.packetRotation();
        if (currentRotation.pitch() > -89.5f || serverRotation == null || serverRotation.pitch() > -89.5f
                || packetRotation != null && packetRotation.pitch() > -89.5f
                || ticks - switchedAt < switchDelay.get()) {
            readyAt = -1;
            return;
        }
        if (readyAt < 0) readyAt = ticks;
        if (ticks - readyAt < throwDelay.get() || player.getCooldowns().isOnCooldown(player.getMainHandItem())) return;
        var animation = player.getMainHandItem().getInteractAnimation();
        var result = client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        if (!(result instanceof InteractionResult.Success success)) {
            setEnabled(false);
            return;
        }
        if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            player.swing(InteractionHand.MAIN_HAND, animation, false);
        }
        readyAt = -1;
        if (windCharge) {
            finished = true;
        } else {
            windCharge = true;
            switchPending = true;
        }
    }

    private boolean select(Minecraft client, Item item) {
        var inventory = player.getInventory();
        int slot = InventoryUtils.findHotbar(inventory, item);
        if (slot < 0) return false;
        if (!SlotSelection.select(this, inventory, slot)) return false;
        SlotSelection.syncCarriedItem(client);
        selectedSlot = slot;
        switchedAt = ticks;
        return true;
    }

    private boolean waitingForScreen(Minecraft client) {
        return originalSlot < 0 && client.gui.screen() != null && ready(client);
    }

    private boolean valid(Minecraft client) {
        return ready(client) && Gameplay.focused(client);
    }

    private boolean ready(Minecraft client) {
        return player != null && client.player == player && level != null && client.level == level
                && client.gameMode != null && player.isAlive() && !player.isSpectator()
                && !player.isUsingItem() && !player.isHandsBusy() && !player.isPassenger()
                && player.containerMenu == player.inventoryMenu;
    }

    @Override
    protected void onDisable() {
        var client = Minecraft.getInstance();
        boolean current = player != null && client.player == player && client.level == level;
        boolean restore = current && originalSlot >= 0 && player.getInventory().getSelectedSlot() == selectedSlot;
        SlotSelection.complete(this, restore ? originalSlot : -1);
        if (restore) SlotSelection.syncCarriedItem(client);
        player = null;
        level = null;
        originalSlot = selectedSlot = -1;
    }

    @EventHandler(priority = 70)
    private void onPreTick(PreTickEvent event) {
        beforeTick(event.client());
    }
}
