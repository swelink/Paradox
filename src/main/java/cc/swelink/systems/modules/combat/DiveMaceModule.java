package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TargetRefreshEvent;
import cc.swelink.mixin.LocalPlayerInputAccess;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.movement.ElytraBoostModule;
import cc.swelink.utils.combat.EnemyRaycast;
import cc.swelink.utils.combat.TargetFinder;
import cc.swelink.utils.player.AttributeSwap;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.projectile.WeaponShot;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationSettings;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class DiveMaceModule extends Module {
    private enum State {
        IDLE, TAKEOFF, CLIMB, DIVE, STRIKE, RECOVER
    }

    private static final int ROTATION_PRIORITY = 6;
    private static final int STRIKE_TIMEOUT_TICKS = 8;
    private static final int RELEASE_TICKS = 2;
    private static final int ROCKET_ARRIVAL_MARGIN_TICKS = 5;
    private static final int MILLISECONDS_PER_TICK = 50;
    private static final int CHEST_MENU_SLOT = 6;
    private static final int MAX_TAKEOFF_TRIES = 4;
    private static final int TAKEOFF_TIMEOUT = 40;
    private static final int RETRY_TICKS = 100;
    private static final int EQUIP_SETTLE_TICKS = 2;
    private static final int MAX_LEAD_TICKS = 20;
    private static final int GROUND_MARGIN = 2;
    private static final float CLIMB_PITCH = -50;
    private static final float STRAIGHT_UP = -90;
    private static final double MIN_FALL = 1;
    private static final double MIN_DIVE_SPEED = 0.5;
    private static final double MIN_ABOVE = 1;
    private static final double ROCKET_MIN_DISTANCE = 8;
    private static final double LINE_UP = 0.9;
    private final IntSetting height = addSetting(new IntSetting("Height", 10, 3, 15));
    private final ModeSetting swapMode = addSetting(new ModeSetting("Swap mode", "Vanilla", "Vanilla", "Click"));
    private final BooleanSetting shieldBreaker = addSetting(new BooleanSetting("Shield breaker", true));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final IntSetting switchDelay = addSetting(new IntSetting("Switch delay", 1, 0, 20));
    private final IntSetting swapBackDelay = addSetting(new IntSetting("Swap back delay", 2, 1, 20));
    private final RotationSettings rotations = new RotationSettings(this::addSetting);
    private LocalPlayer owner;
    private State state = State.IDLE;
    private Player target;
    private boolean swapped;
    private int originalSlot = -1;
    private long ticks;
    private long restoreTick;
    private long rocketWait;
    private long strikeEnd;
    private boolean wantsRocket;
    private boolean rescue;
    private boolean restoreElytra;
    private boolean shieldHit;
    private boolean launched;
    private long maceTick;
    private long retryTick;
    private long glideReadyTick;
    private int takeoffTries;
    private int takeoffWait;

    public DiveMaceModule() {
        super("DiveMace", "Elytra dives with a mace", Category.COMBAT);
        switchDelay.visibleWhen(shieldBreaker::get);
    }

    @Override
    protected void onDisable() {
        var client = Minecraft.getInstance();
        restoreElytra |= client.player != null && client.player == owner
                && (state == State.STRIKE || state == State.RECOVER);
        restoreSlot(client);
        state = State.IDLE;
        target = null;
        wantsRocket = false;
        rescue = false;
        launched = false;
        Paradox.ROTATIONS.clear();
    }

    public void tickSwap(Minecraft client) {
        ticks++;
        if (originalSlot >= 0 && ticks >= restoreTick) restoreSlot(client);
    }

    @Override
    public void tick(Minecraft client) {
        var player = client.player;
        if (player == null || player != owner || client.level == null || client.gameMode == null
                || !Gameplay.focused(client) || !validTarget(client, target)) return;
        switch (state) {
            case TAKEOFF -> aim(client, launching(player) ? lookAtTarget(client) : new Rotation(lookAtTarget(client).yaw(), CLIMB_PITCH));
            case CLIMB -> {
                if (launching(player)) {
                    aim(client, lookAtTarget(client));
                    return;
                }
                double above = player.getY() - target.getY();
                double horizontal = player.position().subtract(target.position()).horizontalDistance();
                float climb = horizontal <= height.get() ? STRAIGHT_UP : CLIMB_PITCH;
                aim(client, new Rotation(lookAtTarget(client).yaw(), above < height.get() ? climb : 0));
            }
            case DIVE, STRIKE, RECOVER -> aim(client, RotationUtils.lookAt(player.getEyePosition(), predictedPoint(client)));
            case IDLE -> { }
        }
    }

    @Override
    public void tickInput(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null) return;
        if (player != owner) {
            owner = player;
            state = State.IDLE;
            target = null;
            swapped = false;
            rescue = false;
            restoreElytra = false;
        }
        wantsRocket = false;
        if (player.isFallFlying() || player.onGround()) rescue = false;
        if (!Gameplay.focused(client)) return;
        if (restoreElytra) {
            restoreElytra = false;
            equipElytra(client);
            return;
        }
        if (!isEnabled()) return;
        if (state != State.IDLE && !validTarget(client, target)) {
            target = null;
            if (state == State.STRIKE || state == State.RECOVER) {
                equipElytra(client);
                rescue = true;
                state = State.IDLE;
                return;
            }
            state = State.IDLE;
        }
        if ((state == State.TAKEOFF || state == State.CLIMB || state == State.DIVE) && !canFly(client)) {
            abort(player);
            return;
        }
        switch (state) {
            case IDLE -> idle(client);
            case TAKEOFF -> takeoff(client);
            case CLIMB -> climb(client);
            case DIVE -> dive(client);
            case STRIKE -> strike(client);
            case RECOVER -> finishStrike(client);
        }
        if (state == State.STRIKE) {
            attack(client);
        } else if (wantsRocket && player.isFallFlying()) {
            fireRocket(client);
        }
    }

    private void idle(Minecraft client) {
        var player = client.player;
        if (swapped && player.onGround()) {
            swapChest(client);
            return;
        }
        if (ticks < retryTick) return;
        var found = findTarget(client);
        if (found == null || !equipped(client)) return;
        target = found;
        takeoffTries = 0;
        takeoffWait = 0;
        Paradox.MODULES.get(StickyTargetModule.class).attacked(client, found.getId());
        state = State.TAKEOFF;
    }

    private boolean equipped(Minecraft client) {
        var player = client.player;
        if (InventoryUtils.findHotbar(player.getInventory(), Items.MACE) < 0 || !hasRockets(player)) return false;
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (usable(chest)) return bringToHotbar(client, DiveMaceModule::chestplate);
        return bringToHotbar(client, DiveMaceModule::usable) && swapChest(client);
    }

    private static boolean chestplate(ItemStack stack) {
        return stack.is(ItemTags.CHEST_ARMOR) && !stack.is(Items.ELYTRA);
    }

    private static boolean bringToHotbar(Minecraft client, Predicate<ItemStack> matches) {
        var inventory = client.player.getInventory();
        if (InventoryUtils.findHotbar(inventory, matches) >= 0) return true;
        int stored = InventoryUtils.findInventory(inventory, matches);
        int empty = InventoryUtils.findHotbar(inventory, ItemStack::isEmpty);
        return stored >= 0 && empty >= 0 && InventoryUtils.swapPlayerInventoryItem(client, stored, empty);
    }

    private static boolean usable(ItemStack stack) {
        return stack.is(Items.ELYTRA) && LivingEntity.canGlideUsing(stack, EquipmentSlot.CHEST);
    }

    private boolean canFly(Minecraft client) {
        var player = client.player;
        return usable(player.getItemBySlot(EquipmentSlot.CHEST))
                && InventoryUtils.findHotbar(player.getInventory(), Items.MACE) >= 0
                && (hasRockets(player) || ElytraBoostModule.hasRocket(player));
    }

    private void abort(LocalPlayer player) {
        target = null;
        state = State.IDLE;
        retryTick = ticks + RETRY_TICKS;
        rescue = !player.onGround() && !player.isFallFlying();
    }

    private void takeoff(Minecraft client) {
        if (!client.player.isFallFlying()) {
            if (++takeoffWait > TAKEOFF_TIMEOUT) abort(client.player);
            return;
        }
        takeoffWait = 0;
        if (++takeoffTries > MAX_TAKEOFF_TRIES) {
            abort(client.player);
            return;
        }
        state = State.CLIMB;
    }

    private void climb(Minecraft client) {
        var player = client.player;
        if (!player.isFallFlying()) {
            state = State.TAKEOFF;
            return;
        }
        double above = player.getY() - target.getY();
        double horizontal = player.position().subtract(target.position()).horizontalDistance();
        if (above >= height.get() && horizontal <= Math.max(height.get(), above)) {
            takeoffTries = 0;
            state = State.DIVE;
            dive(client);
            return;
        }
        if (launching(player)) return;
        requestRocket(player);
    }

    private void dive(Minecraft client) {
        var player = client.player;
        if (!player.isFallFlying()) {
            state = State.TAKEOFF;
            return;
        }
        var eyes = player.getEyePosition();
        var point = predictedPoint(client);
        var offset = point.subtract(eyes);
        var velocity = player.getDeltaMovement();
        double distance = offset.length();
        double speed = velocity.length();
        if (player.getY() - target.getY() < MIN_ABOVE || clearance(client) < GROUND_MARGIN) {
            state = State.CLIMB;
            return;
        }
        double closing = distance > 0 ? velocity.dot(offset.normalize()) : 0;
        if (closing <= 0 || distance > ROCKET_MIN_DISTANCE) requestRocket(player);
        boolean inReleaseRange = distance <= player.entityInteractionRange() + speed * RELEASE_TICKS;
        if (!inReleaseRange) return;
        boolean lined = speed > 0 && velocity.normalize().dot(offset.normalize()) >= LINE_UP;
        if (!lined) {
            if (player.getY() - target.getY() < height.get()) state = State.CLIMB;
            return;
        }
        if (velocity.y > -MIN_DIVE_SPEED || player.fallDistance < MIN_FALL || clickMode() && !quiet(player)) return;
        wantsRocket = false;
        if (swapChest(client)) {
            state = State.STRIKE;
            strikeEnd = ticks + STRIKE_TIMEOUT_TICKS;
            shieldHit = false;
            maceTick = 0;
        } else {
            state = State.CLIMB;
        }
    }

    private void strike(Minecraft client) {
        var player = client.player;
        if (ticks >= strikeEnd || player.onGround() || player.getDeltaMovement().y >= 0) finishStrike(client);
    }

    private void finishStrike(Minecraft client) {
        if (busy(client)) return;
        equipElytra(client);
        state = State.TAKEOFF;
        launched = client.player.getDeltaMovement().y > 0;
    }

    private boolean launching(LocalPlayer player) {
        if (launched && (player.getDeltaMovement().y <= 0 || state != State.TAKEOFF && state != State.CLIMB)) launched = false;
        return launched;
    }

    private void attack(Minecraft client) {
        var player = client.player;
        if (busy(client) || ticks < strikeEnd - STRIKE_TIMEOUT_TICKS + RELEASE_TICKS || player.isFallFlying()
                || player.fallDistance <= 1.5 || ticks < maceTick
                || SlotSelection.isOwned() && !SlotSelection.owns(this, player.getInventory())) {
            return;
        }
        var rotation = rotations.current(client);
        var traced = raycast.target() ? EnemyRaycast.trace(client, target, rotation) : RotationRaycast.traceHeldItem(rotation, 1);
        if (!(traced instanceof EntityHitResult hit) || hit.getEntity() != target) return;
        if (!shieldHit && shieldBreaker.get() && target.isBlocking() && target.getUseItem().is(Items.SHIELD)) {
            int axe = InventoryUtils.findHotbar(player.getInventory(), stack -> stack.is(ItemTags.AXES));
            if (axe >= 0 && holdSlot(client) && AttributeSwap.attackWithSlot(client, target, axe)) {
                shieldHit = true;
                maceTick = ticks + switchDelay.get();
                restoreTick = ticks + STRIKE_TIMEOUT_TICKS;
                return;
            }
        }
        int mace = InventoryUtils.findHotbar(player.getInventory(), Items.MACE);
        if (mace < 0 || !holdSlot(client)) return;
        if (!AttributeSwap.attackWithSlot(client, target, mace)) {
            restoreSlot(client);
            return;
        }
        restoreTick = ticks + swapBackDelay.get();
        state = State.RECOVER;
    }

    private boolean holdSlot(Minecraft client) {
        return originalSlot >= 0 || beginSlot(client);
    }

    private void fireRocket(Minecraft client) {
        var player = client.player;
        if (busy(client)) return;
        if (player.getMainHandItem().is(Items.FIREWORK_ROCKET)) {
            useRocket(client, InteractionHand.MAIN_HAND);
            return;
        }
        if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
            useRocket(client, InteractionHand.OFF_HAND);
            return;
        }
        int rocket = InventoryUtils.findHotbar(player.getInventory(), Items.FIREWORK_ROCKET);
        if (rocket < 0 || !holdSlot(client)) return;
        AttributeSwap.swap(client, rocket);
        useRocket(client, InteractionHand.MAIN_HAND);
        restoreTick = Math.max(restoreTick, ticks + 1);
    }

    private void useRocket(Minecraft client, InteractionHand hand) {
        useHeld(client, hand);
        rocketWait = ticks + rocketArrivalTicks(client);
    }

    private static int rocketArrivalTicks(Minecraft client) {
        var info = client.getConnection().getPlayerInfo(client.player.getUUID());
        int latency = info == null ? 0 : Math.max(info.getLatency(), 0);
        return latency / MILLISECONDS_PER_TICK + ROCKET_ARRIVAL_MARGIN_TICKS;
    }

    private static boolean useHeld(Minecraft client, InteractionHand hand) {
        var player = client.player;
        var animation = player.getItemInHand(hand).getInteractAnimation();
        if (!(client.gameMode.useItem(player, hand) instanceof InteractionResult.Success success)) return false;
        if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) player.swing(hand, animation, false);
        player.itemUsed(hand);
        return true;
    }

    private static boolean busy(Minecraft client) {
        var player = client.player;
        return player.isUsingItem() || player.isHandsBusy() || client.gameMode.isDestroying();
    }

    private boolean beginSlot(Minecraft client) {
        var inventory = client.player.getInventory();
        if (!SlotSelection.begin(this, inventory)) return false;
        originalSlot = inventory.getSelectedSlot();
        return true;
    }

    private void restoreSlot(Minecraft client) {
        if (originalSlot < 0) return;
        if (client.player != null && client.player == owner && SlotSelection.owns(this, client.player.getInventory())) {
            AttributeSwap.swap(client, originalSlot);
        }
        SlotSelection.complete(this, -1);
        originalSlot = -1;
    }

    private void requestRocket(LocalPlayer player) {
        wantsRocket = ticks >= rocketWait && !ElytraBoostModule.hasRocket(player);
    }

    private void equipElytra(Minecraft client) {
        if (!client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) swapChest(client);
    }

    private boolean swapChest(Minecraft client) {
        if (busy(client)) return false;
        var player = client.player;
        var inventory = player.getInventory();
        boolean wearingElytra = player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        int slot = InventoryUtils.findHotbar(inventory, wearingElytra ? DiveMaceModule::chestplate : DiveMaceModule::usable);
        if (slot < 0) return false;
        if (clickMode() && slot != inventory.getSelectedSlot() && quiet(player) && player.containerMenu == player.inventoryMenu) {
            client.gameMode.handleContainerInput(player.inventoryMenu.containerId, CHEST_MENU_SLOT, slot, ContainerInput.SWAP, player);
        } else if (!useChest(client, slot)) {
            return false;
        }
        swapped = !swapped;
        if (!wearingElytra) glideReadyTick = ticks + EQUIP_SETTLE_TICKS;
        return true;
    }

    private boolean useChest(Minecraft client, int slot) {
        if (!holdSlot(client)) return false;
        AttributeSwap.swap(client, slot);
        restoreTick = Math.max(restoreTick, ticks + 1);
        return useHeld(client, InteractionHand.MAIN_HAND);
    }

    private boolean clickMode() {
        return swapMode.get().equals("Click");
    }

    private static boolean quiet(LocalPlayer player) {
        var sent = ((LocalPlayerInputAccess) player).paradox$lastSentInput();
        return !player.isSprinting() && !player.isShiftKeyDown() && !sent.shift()
                && !sent.forward() && !sent.backward() && !sent.left() && !sent.right();
    }

    private static boolean hasRockets(LocalPlayer player) {
        return player.getOffhandItem().is(Items.FIREWORK_ROCKET)
                || InventoryUtils.findHotbar(player.getInventory(), Items.FIREWORK_ROCKET) >= 0;
    }

    private void aim(Minecraft client, Rotation rotation) {
        Paradox.TARGETS.report(target);
        rotations.requestGlide(client, rotation, ROTATION_PRIORITY);
    }

    private Rotation lookAtTarget(Minecraft client) {
        return RotationUtils.lookAt(client.player.getEyePosition(), target.getBoundingBox().getCenter());
    }

    private Vec3 predictedPoint(Minecraft client) {
        var player = client.player;
        var center = target.getBoundingBox().getCenter();
        double speed = Math.max(player.getDeltaMovement().length(), 1);
        double lead = Math.min(player.getEyePosition().distanceTo(center) / speed, MAX_LEAD_TICKS);
        return center.add(WeaponShot.motion(target).scale(lead));
    }

    private static int clearance(Minecraft client) {
        var cursor = new BlockPos.MutableBlockPos();
        var origin = client.player.blockPosition();
        for (int depth = 1; depth <= GROUND_MARGIN; depth++) {
            cursor.set(origin.getX(), origin.getY() - depth, origin.getZ());
            if (!client.level.getBlockState(cursor).getCollisionShape(client.level, cursor).isEmpty()) return depth - 1;
        }
        return GROUND_MARGIN;
    }

    private boolean validTarget(Minecraft client, Player player) {
        return TargetFinder.eligible(client, player, raycast.target());
    }

    private Player findTarget(Minecraft client) {
        return TargetFinder.nearest(client, raycast.target(), player -> true, client.player::distanceToSqr);
    }

    private Input prepareInput(Minecraft client, Input input) {
        var player = client.player;
        if (isEnabled() && player != null && clickMode() && (state == State.DIVE || state == State.STRIKE || state == State.RECOVER)) {
            return new Input(false, false, false, false, false, false, false);
        }
        if (!isEnabled() || state != State.TAKEOFF && !rescue || player == null || player.isFallFlying()) return input;
        boolean pressed = ((LocalPlayerInputAccess) player).paradox$lastSentInput().jump();
        boolean jump = player.onGround() ? state == State.TAKEOFF : !pressed && ticks >= glideReadyTick;
        return new Input(input.forward(), input.backward(), input.left(), input.right(), jump, input.shift(), input.sprint());
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 181)
    private void onPreTick(PreTickEvent event) {
        tickSwap(event.client());
    }

    @EventHandler(priority = 65)
    private void onTargetRefresh(TargetRefreshEvent event) {
        if (!isEnabled() && restoreElytra) tickInput(event.client());
    }

    @EventHandler(priority = 65)
    private void onInput(InputEvent event) {
        event.input(prepareInput(event.client(), event.input()));
    }
}
