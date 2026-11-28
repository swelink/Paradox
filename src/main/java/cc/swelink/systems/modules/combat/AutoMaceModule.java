package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.utils.combat.AttackUtils;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.EnemyRaycast;
import cc.swelink.utils.combat.TargetFinder;
import cc.swelink.utils.player.AttributeSwap;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationSettings;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class AutoMaceModule extends Module {
    private final BooleanSetting shieldBreaker = addSetting(new BooleanSetting("Shield breaker", true));
    private final IntSetting switchDelay = addSetting(new IntSetting("Switch delay", 1, 1, 20));
    private final IntSetting swapBackDelay = addSetting(new IntSetting("Swap back delay", 2, 1, 20));
    private final IntSetting range = addSetting(new IntSetting("Range", 6, 1, 6));
    private final IntSetting fov = addSetting(new IntSetting("FOV", 360, 10, 360));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final ModeSetting aimMode = addSetting(new ModeSetting("Aim mode", "Multipoint", "Multipoint", "Head"));
    private final RotationSettings rotations = new RotationSettings(this::addSetting);
    private LocalPlayer swappedPlayer;
    private LocalPlayer sourcePlayer;
    private Item sourceItem;
    private int sourceSlot = -1;
    private long sourceReadyTick;
    private Player attackTarget;
    private AttackRange swapRange;
    private long swapRangeTick;
    private int originalSlot = -1;
    private int selectedSlot = -1;
    private boolean waitingForMace;
    private long ticks;
    private long switchTick;
    private long restoreTick;

    public AutoMaceModule() {
        super("AutoMace", "Automacing the mace", Category.COMBAT);
        switchDelay.visibleWhen(shieldBreaker::get);
    }

    @Override
    protected void onDisable() {
        restore(Minecraft.getInstance());
        sourcePlayer = null;
        sourceItem = null;
        sourceSlot = -1;
        Paradox.ROTATIONS.clear();
    }

    @Override
    public void tick(Minecraft client) {
        if (!canSmash(client) || (SlotSelection.isOwned()
                && !SlotSelection.owns(this, client.player.getInventory()))) {
            return;
        }
        if (originalSlot >= 0 && !waitingForMace) {
            return;
        }
        var target = waitingForMace ? attackTarget : findTarget(client);
        if (!validTarget(client, target)) {
            return;
        }
        Paradox.TARGETS.report(target);
        var eyes = client.player.getEyePosition();
        var point = getAimPoint(target, eyes);
        rotations.request(client, RotationUtils.lookAt(eyes, point), 2);
    }

    @Override
    public void tickInput(Minecraft client) {
        if (originalSlot < 0) {
            updateSourceItem(client);
            if (sourcePlayer != client.player || ticks < sourceReadyTick) {
                return;
            }
        }
        if (originalSlot >= 0 && !waitingForMace) {
            return;
        }
        if (!canSmash(client)) {
            if (waitingForMace) {
                restore(client);
            }
            return;
        }
        var player = client.player;
        if (waitingForMace) {
            if (!isCurrentSwap(client) || !validTarget(client, attackTarget)) {
                restore(client);
            } else if (ticks >= switchTick && currentTarget(client) == attackTarget) {
                attackMace(client);
            }
            return;
        }
        if (SlotSelection.isOwned()) {
            return;
        }
        var target = currentTarget(client);
        if (!validTarget(client, target)) {
            return;
        }
        boolean breakShield = shieldBreaker.get() && target.isBlocking() && target.getUseItem().is(Items.SHIELD);
        if (breakShield && InventoryUtils.findHotbar(player.getInventory(), stack -> stack.is(ItemTags.AXES)) < 0) {
            return;
        }
        if (!SlotSelection.begin(this, player.getInventory())) {
            return;
        }
        swappedPlayer = player;
        swapRange = player.getMainHandItem().get(DataComponents.ATTACK_RANGE);
        if (swapRange == null) {
            swapRange = AttackRange.defaultFor(player);
        }
        swapRangeTick = ticks;
        originalSlot = selectedSlot = player.getInventory().getSelectedSlot();
        attackTarget = target;
        if (breakShield) {
            int axe = prepareWeapon(client, stack -> stack.is(ItemTags.AXES));
            if (axe < 0 || !AttributeSwap.attackWithSlot(client, target, axe)) {
                restore(client);
                return;
            }
            waitingForMace = true;
            switchTick = ticks + switchDelay.get();
            return;
        }
        attackMace(client);
    }

    private void attackMace(Minecraft client) {
        if (!canSmash(client) || !validAttackTarget(client, attackTarget)) {
            restore(client);
            return;
        }
        int mace = prepareWeapon(client, stack -> stack.is(Items.MACE));
        if (mace < 0 || !AttributeSwap.attackWithSlot(client, attackTarget, mace)) {
            restore(client);
            return;
        }
        waitingForMace = false;
        restoreTick = ticks + swapBackDelay.get();
        if (swapBackDelay.get() == 0) {
            restore(client);
        }
    }

    public void tickSwap(Minecraft client) {
        ticks++;
        if (originalSlot < 0) {
            if (isEnabled()) {
                updateSourceItem(client);
            }
            return;
        }
        if (!isCurrentSwap(client) || !swappedPlayer.isAlive()
                || (waitingForMace && (!canSmash(client) || !validTarget(client, attackTarget)))
                || (!waitingForMace && ticks >= restoreTick)) {
            restore(client);
        }
    }

    private void updateSourceItem(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null || SlotSelection.isOwned()) {
            sourcePlayer = null;
            sourceItem = null;
            sourceSlot = -1;
            return;
        }
        int slot = player.getInventory().getSelectedSlot();
        var item = player.getMainHandItem().getItem();
        if (sourcePlayer != player || sourceSlot != slot || sourceItem != item) {
            SlotSelection.syncCarriedItem(client);
            sourcePlayer = player;
            sourceSlot = slot;
            sourceItem = item;
            sourceReadyTick = ticks + 1;
        }
    }

    private static boolean canSmash(Minecraft client) {
        var player = client.player;
        return Gameplay.canAct(client) && client.gameMode != null
                && !player.isUsingItem() && !player.isHandsBusy()
                && !(player.isFallFlying() && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA))
                && player.containerMenu == player.inventoryMenu && !player.onGround()
                && player.fallDistance >= 1.5 && player.getDeltaMovement().y < 0
                && InventoryUtils.findHotbar(player.getInventory(), stack -> stack.is(Items.MACE)) >= 0;
    }

    private Player currentTarget(Minecraft client) {
        var target = waitingForMace ? attackTarget : findTarget(client);
        if (!validAttackTarget(client, target)) {
            return null;
        }
        Rotation rotation = rotations.silent() ? Paradox.ROTATIONS.packetRotation() : null;
        if (rotation == null) {
            rotation = new Rotation(client.player.getYRot(), client.player.getXRot());
        }
        var hit = raycast.target() ? EnemyRaycast.trace(client, target, rotation) : RotationRaycast.traceHeldItem(rotation, 1);
        return hit instanceof EntityHitResult entityHit && entityHit.getEntity() == target ? target : null;
    }

    private boolean validTarget(Minecraft client, Player target) {
        return TargetFinder.eligible(client, target, raycast.target()) && inRange(client, target);
    }

    private boolean inRange(Minecraft client, Player target) {
        return TargetFinder.hitboxDistanceSqr(client.player.getEyePosition(), target) <= range.get() * range.get();
    }

    private boolean validAttackTarget(Minecraft client, Player target) {
        return validTarget(client, target) && isInHeldItemRange(client.player, target);
    }

    private boolean isInHeldItemRange(LocalPlayer player, Player target) {
        var attackRange = swapRange != null && swapRangeTick == ticks
                ? swapRange : player.getMainHandItem().get(DataComponents.ATTACK_RANGE);
        return attackRange != null ? attackRange.isInRange(player, target.getBoundingBox(), 0)
                : player.isWithinEntityInteractionRange(target, 0);
    }

    private Player findTarget(Minecraft client) {
        var eyes = client.player.getEyePosition();
        return TargetFinder.nearest(client, raycast.target(), target -> inRange(client, target)
                && Math.abs(RotationUtils.yawDifference(client.player.getYRot(),
                        RotationUtils.lookAt(eyes, getAimPoint(target, eyes)).yaw())) <= fov.get() / 2f,
                target -> TargetFinder.hitboxDistanceSqr(eyes, target));
    }

    private Vec3 getAimPoint(Player target, Vec3 eyes) {
        return AttackUtils.aimPoint(target, eyes, aimMode.get().equals("Head"), 0);
    }

    private int prepareWeapon(Minecraft client, Predicate<ItemStack> matches) {
        int slot = InventoryUtils.findHotbar(client.player.getInventory(), matches);
        if (slot < 0) {
            return -1;
        }
        selectedSlot = slot;
        return slot;
    }

    private boolean isCurrentSwap(Minecraft client) {
        return client.player == swappedPlayer && client.level == swappedPlayer.level()
                && SlotSelection.owns(this, swappedPlayer.getInventory())
                && swappedPlayer.getInventory().getSelectedSlot() == selectedSlot;
    }

    private void restore(Minecraft client) {
        if (originalSlot < 0) {
            return;
        }
        if (client.player == swappedPlayer && client.level == swappedPlayer.level()) {
            if (isCurrentSwap(client)) {
                AttributeSwap.swap(client, originalSlot);
                selectedSlot = originalSlot;
            }
        }
        SlotSelection.complete(this, -1);
        swappedPlayer = null;
        attackTarget = null;
        swapRange = null;
        swapRangeTick = 0;
        originalSlot = selectedSlot = -1;
        waitingForMace = false;
        switchTick = restoreTick = 0;
        sourcePlayer = null;
        if (isEnabled()) {
            updateSourceItem(client);
        }
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 180)
    private void onPreTick(PreTickEvent event) {
        tickSwap(event.client());
    }
}
