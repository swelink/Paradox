package cc.swelink.systems.modules.player;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class AutoExpModule extends Module {
    private static final int PRIORITY = -100;
    private static final float DOWN = 90;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final PercentSetting durability = addSetting(new PercentSetting("Durability", 50));
    private final IntSetting switchDelay = addSetting(new IntSetting("Switch delay", 1, 0, 20));
    private final IntSetting throwDelay = addSetting(new IntSetting("Throw delay", 1, 1, 20));
    private final IntSetting swapBackDelay = addSetting(new IntSetting("Swap back delay", 2, 1, 20));
    private final RotationSettings rotations = new RotationSettings(this::addSetting);
    private boolean repairing;
    private InteractionHand hand;
    private int originalSlot = -1;
    private int selectedSlot = -1;
    private int ticks;
    private int switchedAt;
    private int thrownAt = Integer.MIN_VALUE / 2;
    private int finishedAt = -1;

    public AutoExpModule() {
        super("AutoExp", "Throws exp to repair armor", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        repairing = false;
        hand = null;
        originalSlot = selectedSlot = -1;
        finishedAt = -1;
    }

    public void beforeTick(Minecraft client) {
        ticks++;
        if (hand == null) return;
        if (!ready(client) || Paradox.ROTATIONS.hasHigherPriority(PRIORITY) || !wanted(client.player)) finish(client);
        if (finishedAt >= 0 && ticks - finishedAt >= swapBackDelay.get()) release(client);
    }

    @Override
    public void tick(Minecraft client) {
        if (hand == null || finishedAt >= 0 || !ready(client)) return;
        rotations.request(client, new Rotation(client.player.getYRot(), DOWN), PRIORITY);
    }

    @Override
    public void tickInput(Minecraft client) {
        if (!ready(client)) return;
        var player = client.player;
        if (hand == null) {
            if (finishedAt < 0 && !Paradox.ROTATIONS.hasHigherPriority(PRIORITY) && wanted(player)) start(client);
            return;
        }
        if (finishedAt >= 0) return;
        if (hand == InteractionHand.MAIN_HAND && (!SlotSelection.owns(this, player.getInventory())
                || player.getInventory().getSelectedSlot() != selectedSlot)) {
            hand = null;
            originalSlot = selectedSlot = -1;
            return;
        }
        var stack = player.getItemInHand(hand);
        if (!stack.is(Items.EXPERIENCE_BOTTLE)) {
            finish(client);
            return;
        }
        var current = rotations.current(client);
        var server = Paradox.ROTATIONS.serverRotation();
        var packet = Paradox.ROTATIONS.packetRotation();
        if (current.pitch() < DOWN - 0.5f || rotations.silent() && (server == null || server.pitch() < DOWN - 0.5f
                || packet != null && packet.pitch() < DOWN - 0.5f)
                || ticks - switchedAt < switchDelay.get() || ticks - thrownAt < throwDelay.get()) {
            return;
        }
        var animation = stack.getInteractAnimation();
        var result = client.gameMode.useItem(player, hand);
        if (!(result instanceof InteractionResult.Success success)) return;
        thrownAt = ticks;
        if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) player.swing(hand, animation, false);
    }

    private void start(Minecraft client) {
        var player = client.player;
        if (player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE)) {
            hand = InteractionHand.OFF_HAND;
            switchedAt = ticks - switchDelay.get();
            return;
        }
        var inventory = player.getInventory();
        int slot = InventoryUtils.findHotbar(inventory, Items.EXPERIENCE_BOTTLE);
        if (slot < 0 || !SlotSelection.begin(this, inventory)) return;
        originalSlot = inventory.getSelectedSlot();
        if (!SlotSelection.select(this, inventory, slot)) {
            SlotSelection.complete(this, -1);
            originalSlot = -1;
            return;
        }
        SlotSelection.syncCarriedItem(client);
        selectedSlot = slot;
        hand = InteractionHand.MAIN_HAND;
        switchedAt = slot == originalSlot ? ticks - switchDelay.get() : ticks;
    }

    private boolean wanted(LocalPlayer player) {
        boolean anyMending = false;
        boolean low = false;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isDamaged() || !mending(player, stack)) continue;
            anyMending = true;
            float left = 1 - (float) stack.getDamageValue() / stack.getMaxDamage();
            if (left * 100 < durability.get()) low = true;
        }
        if (!anyMending) repairing = false;
        else if (low) repairing = true;
        return repairing && hasBottles(player);
    }

    private static boolean hasBottles(LocalPlayer player) {
        return player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE)
                || InventoryUtils.findHotbar(player.getInventory(), Items.EXPERIENCE_BOTTLE) >= 0;
    }

    private static boolean mending(LocalPlayer player, ItemStack stack) {
        var holder = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.MENDING);
        return holder.map(enchantment -> EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) > 0).orElse(false);
    }

    private void finish(Minecraft client) {
        if (finishedAt < 0) finishedAt = ticks;
    }

    private void release(Minecraft client) {
        var player = client.player;
        if (hand == InteractionHand.MAIN_HAND) {
            boolean restore = player != null && SlotSelection.owns(this, player.getInventory())
                    && player.getInventory().getSelectedSlot() == selectedSlot;
            SlotSelection.complete(this, restore ? originalSlot : -1);
            if (restore) SlotSelection.syncCarriedItem(client);
        }
        hand = null;
        originalSlot = selectedSlot = -1;
        finishedAt = -1;
    }

    private static boolean ready(Minecraft client) {
        var player = client.player;
        return Gameplay.canAct(client) && client.gameMode != null
                && !player.isUsingItem() && !player.isHandsBusy() && !player.isPassenger()
                && player.containerMenu == player.inventoryMenu;
    }

    @Override
    protected void onDisable() {
        if (hand != null) release(Minecraft.getInstance());
        repairing = false;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 70)
    private void onPreTick(PreTickEvent event) {
        beforeTick(event.client());
    }
}
