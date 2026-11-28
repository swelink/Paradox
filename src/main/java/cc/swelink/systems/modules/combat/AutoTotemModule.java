package cc.swelink.systems.modules.combat;

import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.events.OverlayRenderEvent;
import cc.swelink.events.PacketSentEvent;
import cc.swelink.mixin.AbstractContainerScreenInvoker;
import cc.swelink.mixin.MouseHandlerAccessor;
import cc.swelink.renderer.FrameClock;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.MillisecondsSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.time.GameTime;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

import org.lwjgl.sdl.SDLMouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public final class AutoTotemModule extends Module {
    private static final int TOTEMGUARD_SPREAD = 200;
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Normal", "Normal", "TotemGuard"));
    private final BooleanSetting offhand = addSetting(new BooleanSetting("Offhand", true));
    private final BooleanSetting silent = addSetting(new BooleanSetting("Silent", false));
    private final ModeSetting silentMode = addSetting(new ModeSetting("Silent mode", "Normal", "Normal", "Grim"));
    private final IntSetting hotbarSlot = addSetting(new IntSetting("Hotbar slot", 0, 0, 9) {
        @Override
        protected String formatValue(int value) {
            return value == 0 ? "Off" : Integer.toString(value);
        }
    }.bounded());
    private final IntSetting delay = addSetting(new IntSetting("Delay (ms)", 100, 0, 1000));
    private final IntSetting mouseSpeed = addSetting(new IntSetting("Mouse speed", 12, 1, 30));
    private final MillisecondsSetting closeDelay = addSetting(new MillisecondsSetting("Close delay", 150, 0, 1000));
    private final IntSetting candidates = addSetting(new IntSetting("Candidates", 4, 1, 9));
    private final BooleanSetting mouseMovement = addSetting(new BooleanSetting("Mouse movement", true));
    private final BooleanSetting lockMouse = addSetting(new BooleanSetting("Lock mouse", true));
    private boolean arrived;
    private boolean sentSprint;
    private Input sentInput = Input.EMPTY;
    private final FrameClock clock = new FrameClock();
    private InventoryScreen screen;
    private boolean opened;
    private boolean silentOpen;
    private boolean clicked;
    private boolean releasedKeys;
    private boolean grimHold;
    private boolean closedThisTick;
    private long wait;
    private Slot target;
    private int destination = -1;
    private long hoverStarted;
    private long swapped;
    private double cursorX = Double.NaN;
    private double cursorY = Double.NaN;

    public AutoTotemModule() {
        super("AutoTotem", "Puts a totem in offhand or slot", Category.COMBAT);
        silentMode.visibleWhen(silent::get);
        delay.visibleWhen(() -> !totemGuard());
        mouseSpeed.visibleWhen(() -> !silent() && mouseMovement());
        lockMouse.visibleWhen(() -> !silent() && mouseMovement());
        mouseMovement.visibleWhen(() -> !totemGuard() && !silent());
        candidates.visibleWhen(() -> !totemGuard() && !silent());
        closeDelay.visibleWhen(() -> !totemGuard() && !grimSilent());
    }

    @Override
    public void tick(Minecraft client) {
        var player = client.player;
        if (player == null || client.gameMode == null || !player.isAlive() || player.isSpectator()
                || player.isCreative() || client.gameMode.isServerControlledInventory()) {
            reset(client);
            return;
        }
        if (screen != null && client.gui.screen() != screen) {
            reset(client);
            return;
        }
        if (silentOpen && (client.gui.screen() != null || player.containerMenu != player.inventoryMenu)) {
            closeSilent(client);
            return;
        }
        if (swapped != 0) {
            if (GameTime.nanos() - swapped >= closeDelayNanos()) {
                if (silentOpen) closeSilent(client);
                else if (opened) close(client);
                reset(client);
            }
            return;
        }

        int next = destination(client);
        if (next < 0 || !hasTotem(client, next)) {
            if (silentOpen) closeSilent(client);
            else if (screen != null && opened) close(client);
            reset(client);
            return;
        }

        if (!active()) {
            if (!client.getWindow().isFocused()) return;

            if (player.isUsingItem()) client.gameMode.releaseUsingItem(player);
            if (silent() && client.gui.screen() == null && player.containerMenu == player.inventoryMenu) {
                silentOpen = true;
                if (!grim()) {
                    releasedKeys = true;
                    KeyMapping.releaseAll();
                    if (player.isSprinting()) player.setSprinting(false);
                }
            } else if (client.gui.screen() instanceof InventoryScreen existing) {
                screen = existing;
                opened = false;
            } else if (client.gui.screen() == null) {
                screen = new InventoryScreen(player);
                opened = true;
                client.gui.setScreen(screen);
            } else {
                return;
            }
            destination = -1;
            target = null;
            cursorX = client.mouseHandler.xpos();
            cursorY = client.mouseHandler.ypos();
            clock.reset();
        }
        if (next != destination || !valid(client, target, next)) {
            destination = next;
            target = pickTotem(client, next);
            hoverStarted = 0;
            arrived = false;
        }

        if (target == null || !menu(client).getCarried().isEmpty()) {
            hoverStarted = 0;
            return;
        }
        if (!silentOpen && mouseMovement() && !arrived) {
            hoverStarted = 0;
            return;
        }
        long now = GameTime.nanos();
        if (hoverStarted == 0) {
            hoverStarted = now;
            int ms = totemGuard() ? ThreadLocalRandom.current().nextInt(TOTEMGUARD_SPREAD + 1) : delay.get();
            wait = ms * 1_000_000L;
        }
        if (now - hoverStarted < wait) return;
        if (silentOpen && grim()) grimHold = true;
        if (!still()) return;

        client.gameMode.handleContainerInput(menu(client).containerId, target.index, destination,
                ContainerInput.SWAP, player);
        clicked = true;
        swapped = now;
        if (silentOpen && grim()) closeSilent(client);
    }

    @EventHandler
    private void onOverlayRender(OverlayRenderEvent event) {
        var client = event.client();
        if (!mouseMovement() || screen == null || target == null || swapped != 0 || client.gui.screen() != screen) return;
        var window = client.getWindow();
        var access = (AbstractContainerScreenInvoker) screen;
        double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        double scaleY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
        double goalX = (access.paradox$leftPos() + target.x + 8) * scaleX;
        double goalY = (access.paradox$topPos() + target.y + 8) * scaleY;
        if (Double.isNaN(cursorX)) {
            cursorX = client.mouseHandler.xpos();
            cursorY = client.mouseHandler.ypos();
        }
        float dt = clock.nextDelta();
        double nextX = FrameClock.approach((float) cursorX, (float) goalX, mouseSpeed.get(), dt);
        double nextY = FrameClock.approach((float) cursorY, (float) goalY, mouseSpeed.get(), dt);
        if (Math.abs(nextX - goalX) < 0.5 && Math.abs(nextY - goalY) < 0.5) {
            nextX = goalX;
            nextY = goalY;
            arrived = true;
        }
        if (nextX == cursorX && nextY == cursorY && !locksMouse()) return;
        cursorX = nextX;
        cursorY = nextY;
        SDLMouse.SDL_WarpMouseInWindow(window.handle(), (float) cursorX, (float) cursorY);
        var mouse = (MouseHandlerAccessor) client.mouseHandler;
        mouse.paradox$setXpos(cursorX);
        mouse.paradox$setYpos(cursorY);
    }

    public boolean locksMouse() {
        return silentBusy() || isEnabled() && lockMouse.get() && mouseMovement() && screen != null && target != null
                && swapped == 0 && !Double.isNaN(cursorX);
    }

    public boolean holdsInput() {
        return isEnabled() && (screen != null && (swapped == 0 || totemGuard() && clicked)
                || silentOpen && (!grim() || grimHold));
    }

    public boolean silentBusy() {
        return isEnabled() && silentOpen && !grim();
    }

    public boolean blocksActions() {
        return silentBusy() || isEnabled() && closedThisTick;
    }

    private boolean totemGuard() {
        return mode.get().equals("TotemGuard");
    }

    private void close(Minecraft client) {
        if (clicked && totemGuard()) closedThisTick = true;
        client.player.closeContainer();
    }

    private boolean grim() {
        return silentMode.get().equals("Grim");
    }

    private boolean silent() {
        return silent.get();
    }

    private boolean grimSilent() {
        return grim() && silent();
    }

    private boolean mouseMovement() {
        return !totemGuard() && !silent() && mouseMovement.get();
    }

    private long closeDelayNanos() {
        return totemGuard() ? 0 : closeDelay.get() * 1_000_000L;
    }

    private boolean active() {
        return screen != null || silentOpen;
    }

    private AbstractContainerMenu menu(Minecraft client) {
        return silentOpen ? client.player.inventoryMenu : screen.getMenu();
    }

    private void closeSilent(Minecraft client) {
        if (clicked && client.player != null && client.getConnection() != null) {
            client.getConnection().send(new ServerboundContainerClosePacket(client.player.inventoryMenu.containerId));
            if (totemGuard()) closedThisTick = true;
        }
        boolean restore = releasedKeys && client.gui.screen() == null;
        reset(client);
        if (restore) KeyMapping.setAll();
    }

    @EventHandler(priority = Integer.MAX_VALUE)
    private void onInput(InputEvent event) {
        if (!holdsInput()) return;
        event.input(Input.EMPTY);
        var player = event.client().player;
        if (player != null && player.isSprinting()) player.setSprinting(false);
    }

    private int destination(Minecraft client) {
        var player = client.player;
        var inventory = player.getInventory();
        if (offhand.get() && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return Inventory.SLOT_OFFHAND;
        if (hotbarSlot.get() > 0 && !inventory.getItem(hotbarSlot.get() - 1).is(Items.TOTEM_OF_UNDYING)) {
            return hotbarSlot.get() - 1;
        }
        return -1;
    }

    private boolean valid(Minecraft client, Slot slot, int destination) {
        if (slot == null) return false;
        var player = client.player;
        var inventory = player.getInventory();
        int index = slot.getContainerSlot();
        return slot.container == inventory && index >= 0 && index < Inventory.INVENTORY_SIZE && index != destination
                && slot.getItem().is(Items.TOTEM_OF_UNDYING) && slot.mayPickup(player)
                && slot.mayPlace(inventory.getItem(destination));
    }

    private boolean hasTotem(Minecraft client, int destination) {
        for (var slot : client.player.inventoryMenu.slots) {
            if (valid(client, slot, destination)) return true;
        }
        return false;
    }

    private Slot pickTotem(Minecraft client, int destination) {
        if (silentOpen) {
            var totems = new ArrayList<Slot>();
            for (var slot : client.player.inventoryMenu.slots) {
                if (valid(client, slot, destination)) totems.add(slot);
            }
            return totems.isEmpty() ? null : totems.get(ThreadLocalRandom.current().nextInt(totems.size()));
        }
        var window = client.getWindow();
        var access = (AbstractContainerScreenInvoker) screen;
        double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        double scaleY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
        double mouseX = client.mouseHandler.xpos();
        double mouseY = client.mouseHandler.ypos();
        var totems = new ArrayList<Slot>();
        for (var slot : screen.getMenu().slots) {
            if (valid(client, slot, destination)) totems.add(slot);
        }
        if (totems.isEmpty()) return null;
        totems.sort(Comparator.comparingDouble(slot -> {
            double dx = (access.paradox$leftPos() + slot.x + 8) * scaleX - mouseX;
            double dy = (access.paradox$topPos() + slot.y + 8) * scaleY - mouseY;
            return dx * dx + dy * dy;
        }));
        int pool = totemGuard() || silent() ? totems.size() : Math.min(candidates.get(), totems.size());
        return totems.get(ThreadLocalRandom.current().nextInt(pool));
    }

    private boolean still() {
        return !sentSprint && !sentInput.forward() && !sentInput.backward() && !sentInput.left() && !sentInput.right()
                && !sentInput.jump() && !sentInput.shift();
    }

    @EventHandler
    private void onPacketSent(PacketSentEvent event) {
        if (event.packet() instanceof ServerboundClientTickEndPacket) {
            closedThisTick = false;
        } else if (event.packet() instanceof ServerboundPlayerInputPacket input) {
            sentInput = input.input();
        } else if (event.packet() instanceof ServerboundPlayerCommandPacket command) {
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) sentSprint = true;
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) sentSprint = false;
        }
    }

    @Override
    protected void onEnable() {
        var player = Minecraft.getInstance().player;
        sentSprint = player != null && player.isSprinting();
        sentInput = player == null ? Input.EMPTY : player.input.keyPresses;
        closedThisTick = false;
    }

    @Override
    protected void onDisable() {
        var client = Minecraft.getInstance();
        if (silentOpen) {
            closeSilent(client);
            return;
        }
        if (screen != null && opened && client.player != null && client.gui.screen() == screen) {
            client.player.closeContainer();
        }
        reset(client);
    }

    private void reset(Minecraft client) {
        arrived = false;
        screen = null;
        opened = false;
        silentOpen = false;
        clicked = false;
        releasedKeys = false;
        grimHold = false;
        target = null;
        destination = -1;
        hoverStarted = 0;
        swapped = 0;
        cursorX = Double.NaN;
        cursorY = Double.NaN;
    }
}
