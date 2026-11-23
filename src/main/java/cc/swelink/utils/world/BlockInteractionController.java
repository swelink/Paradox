package cc.swelink.utils.world;

import cc.swelink.Paradox;
import cc.swelink.mixin.KeyMappingAccessor;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.Setting;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationSettings;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public abstract class BlockInteractionController<T extends BlockInteractionController.BlockTarget> {
    private static final int AIM_GRACE_TICKS = 5;
    private static final float CAMERA_TOLERANCE = 0.5f;
    public interface BlockTarget {
        BlockPos position();
        BlockHitResult hit();
        Vec3 aim();
        Item item();

        default int slot() {
            return -1;
        }
    }

    private final IntSetting placeDelay = new IntSetting("Place delay", 1, 0, 20);
    private final IntSetting switchDelay = new IntSetting("Switch delay", 1, 0, 20);
    private final RotationSettings rotations;
    private final Object owner;
    private final BooleanSupplier enabled;
    private final boolean refreshMovingTarget;
    private LocalPlayer swappedPlayer;
    private T target;
    private int originalSlot = -1;
    private int selectedSlot = -1;
    private long ticks;
    private long switchTick;
    private long nextPlace;
    private long manualUseTick = -1;
    private long useTick = -1;
    private boolean suppressHeldUse;
    private boolean restorePending;
    private long tickBoundary;
    private long useBoundary = -1;
    private Rotation lastAim;
    private long lastAimTick;

    protected BlockInteractionController(Object owner, BooleanSupplier enabled,
                                         Consumer<Setting<?>> register, boolean refreshMovingTarget) {
        this.owner = owner;
        this.enabled = enabled;
        this.refreshMovingTarget = refreshMovingTarget;
        register.accept(placeDelay);
        register.accept(switchDelay);
        rotations = new RotationSettings(register);
    }

    public boolean hasTarget() {
        return target != null;
    }

    public RotationSettings rotations() {
        return rotations;
    }

    public int placeDelayTicks() {
        return placeDelay.get();
    }

    public int switchDelayTicks() {
        return switchDelay.get();
    }

    public int predictionWaitTicks() {
        long switchWait = originalSlot < 0 ? switchDelay.get()
                : Math.max(0, switchDelay.get() - (ticks - switchTick));
        return (int) Math.min(3, Math.max(switchWait, nextPlace - ticks));
    }

    protected abstract T findTarget(Minecraft client);
    protected abstract boolean validTarget(Minecraft client, T target);
    protected abstract boolean matches(T target, BlockHitResult hit);
    protected abstract BlockHitResult traceTarget(Minecraft client, T target, Rotation rotation);

    protected boolean restoreAfterUse() {
        return true;
    }

    public void setDefaultDelays(int place, int swap) {
        placeDelay.setValue(place);
        switchDelay.setValue(swap);
    }

    protected int rotationPriority() {
        return 3;
    }

    protected boolean holdSelection() {
        return false;
    }

    protected boolean holdLastAim() {
        return true;
    }

    protected boolean keepAimingBetweenInteractions() {
        return false;
    }

    protected boolean useCamera() {
        return true;
    }

    protected InteractionResult interact(Minecraft client, T target, InteractionHand hand,
                                         BlockHitResult hit, Rotation rotation) {
        return client.gameMode.useItemOn(client.player, hand, hit);
    }

    protected static InteractionResult useHeldItem(Minecraft client, InteractionHand hand, Rotation rotation) {
        var player = client.player;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        player.setYRot(rotation.yaw());
        player.setXRot(rotation.pitch());
        try {
            return client.gameMode.useItem(player, hand);
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    private void refreshAimTarget(Minecraft client) {
        boolean continueAiming = keepAimingBetweenInteractions();
        if (refreshMovingTarget || target == null && (originalSlot < 0 || continueAiming)
                || continueAiming && target != null && !validTarget(client, target)) {
            target = findTarget(client);
        }
    }

    private boolean refreshInputTarget(Minecraft client) {
        if (refreshMovingTarget) {
            target = findTarget(client);
            if (target == null && originalSlot >= 0 && !holdSelection()) {
                restore(client);
            }
            return target != null;
        }
        if (target != null && !validTarget(client, target)) {
            restore(client);
        }
        if (target == null) {
            target = findTarget(client);
        }
        return target != null;
    }

    public void disable() {
        target = null;
        lastAim = null;
        restorePending = originalSlot >= 0;
        suppressHeldUse = false;
        Paradox.ROTATIONS.clear();
    }

    public void tickSwap(Minecraft client) {
        ticks++;
        var use = client.options.keyUse;
        if (!use.isDown() || ((KeyMappingAccessor) use).paradox$getPendingClicks() > 0) {
            suppressHeldUse = false;
        }
        if (!restorePending && target == null && originalSlot >= 0 && !restoreAfterUse()
                && enabled.getAsBoolean() && active(client) && ownsSelection(client)) {
            target = findTarget(client);
        }
        if (restorePending || originalSlot >= 0
                && (!enabled.getAsBoolean() || target == null && !holdSelection() || !active(client) || !ownsSelection(client))) {
            restore(client);
        }
    }

    public boolean allowManualUse(Minecraft client) {
        if (originalSlot >= 0 || useTick == ticks || enabled.getAsBoolean() && suppressHeldUse) {
            return false;
        }
        manualUseTick = ticks;
        return true;
    }

    public void afterTickBoundary() {
        tickBoundary++;
    }

    public void tick(Minecraft client) {
        if (!active(client) || restorePending && !keepAimingBetweenInteractions() || SlotSelection.isOwned()
                && !SlotSelection.owns(owner, client.player.getInventory())) {
            return;
        }
        refreshAimTarget(client);
        if (target != null && validTarget(client, target)) {
            rotate(client);
        } else if (keepAimingBetweenInteractions() && holdLastAim() && lastAim != null
                && ticks - lastAimTick <= AIM_GRACE_TICKS) {
            rotations.request(client, lastAim, rotationPriority());
        }
    }

    public void tickInput(Minecraft client) {
        if (restorePending || manualUseTick == ticks || !prepare(client)) {
            return;
        }
        if (ticks < nextPlace || ticks - switchTick < switchDelay.get()
                || !holding(client, target)) {
            return;
        }
        var camera = new Rotation(client.player.getYRot(), client.player.getXRot());
        var current = rotations.current(client);
        boolean currentOnTarget = matches(target, traceTarget(client, target, current));
        boolean diverted = Math.abs(RotationUtils.yawDifference(current.yaw(), camera.yaw())) > CAMERA_TOLERANCE
                || Math.abs(current.pitch() - camera.pitch()) > CAMERA_TOLERANCE;
        boolean cameraOnTarget = !currentOnTarget && !diverted && useCamera() && matches(target, traceTarget(client, target, camera));
        var rotation = cameraOnTarget ? camera : current;
        var hit = traceTarget(client, target, rotation);
        if (!matches(target, hit)) {
            return;
        }
        if (cameraOnTarget) {
            rotations.holdLocalRotation(client);
        }
        var hand = InteractionHand.MAIN_HAND;
        var stack = client.player.getMainHandItem();
        int count = stack.getCount();
        var animation = stack.getInteractAnimation();
        useTick = ticks;
        useBoundary = tickBoundary;
        suppressHeldUse = client.options.keyUse.isDown();
        var result = interact(client, target, hand, hit, rotation);
        nextPlace = ticks + placeDelay.get();
        if (result instanceof InteractionResult.Success success
                && success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            client.player.swing(hand, animation, false);
            if (!stack.isEmpty() && (stack.getCount() != count || client.player.hasInfiniteMaterials())) {
                client.player.itemUsed(hand);
            }
        }
        target = null;
        restorePending = restoreAfterUse();
    }

    private boolean holding(Minecraft client, T target) {
        return target.slot() >= 0 ? client.player.getInventory().getSelectedSlot() == target.slot()
                : client.player.getMainHandItem().is(target.item());
    }

    private int slotFor(Minecraft client, T target) {
        return target.slot() >= 0 ? target.slot() : InventoryUtils.findHotbar(client.player.getInventory(), target.item());
    }

    private boolean prepare(Minecraft client) {
        if (!active(client)) {
            return false;
        }
        if (originalSlot >= 0 && !ownsSelection(client)) {
            restore(client);
            return false;
        }
        if (SlotSelection.isOwned() && !SlotSelection.owns(owner, client.player.getInventory())) {
            return false;
        }
        if (!refreshInputTarget(client)) {
            return false;
        }
        if (originalSlot >= 0) {
            if (holding(client, target)) {
                return true;
            }
            int next = slotFor(client, target);
            if (next < 0 || restoreAfterUse()) {
                restore(client);
                return false;
            }
            selectedSlot = next;
            SlotSelection.select(owner, client.player.getInventory(), selectedSlot);
            SlotSelection.syncCarriedItem(client);
            switchTick = ticks;
            return true;
        }
        int slot = slotFor(client, target);
        var inventory = client.player.getInventory();
        if (slot < 0 || !SlotSelection.begin(owner, inventory)) {
            target = null;
            return false;
        }
        swappedPlayer = client.player;
        originalSlot = inventory.getSelectedSlot();
        selectedSlot = slot;
        SlotSelection.select(owner, inventory, selectedSlot);
        SlotSelection.syncCarriedItem(client);
        switchTick = ticks;
        return true;
    }

    private void rotate(Minecraft client) {
        var camera = new Rotation(client.player.getYRot(), client.player.getXRot());
        if (keepAimingBetweenInteractions() || !useCamera() || !matches(target, traceTarget(client, target, camera))) {
            lastAim = RotationUtils.lookAt(client.player.getEyePosition(), target.aim());
            lastAimTick = ticks;
            rotations.request(client, lastAim, rotationPriority());
        }
    }

    public static boolean active(Minecraft client) {
        var player = client.player;
        return player != null && client.level != null && client.gameMode != null && player.isAlive()
                && !player.isSpectator() && !player.isUsingItem() && !player.isHandsBusy()
                && client.gui.screen() == null && client.getWindow().isFocused()
                && player.containerMenu == player.inventoryMenu;
    }

    private boolean ownsSelection(Minecraft client) {
        return client.player == swappedPlayer && client.level == swappedPlayer.level()
                && SlotSelection.owns(owner, swappedPlayer.getInventory())
                && swappedPlayer.getInventory().getSelectedSlot() == selectedSlot;
    }

    private void restore(Minecraft client) {
        target = null;
        if (useTick == ticks || useBoundary == tickBoundary) {
            restorePending = true;
            return;
        }
        if (originalSlot < 0) {
            restorePending = false;
            return;
        }
        if (client.player == swappedPlayer && client.level == swappedPlayer.level()) {
            if (ownsSelection(client)) {
                SlotSelection.select(owner, swappedPlayer.getInventory(), originalSlot);
                SlotSelection.syncCarriedItem(client);
            }
        }
        SlotSelection.complete(owner, -1);
        swappedPlayer = null;
        originalSlot = selectedSlot = -1;
        restorePending = false;
    }

}
