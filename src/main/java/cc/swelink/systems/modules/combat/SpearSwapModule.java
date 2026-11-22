package cc.swelink.systems.modules.combat;

import cc.swelink.events.EventHandler;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.BindModeSetting;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.AttackUtils;
import cc.swelink.utils.player.AttributeSwap;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class SpearSwapModule extends Module {
    private final BooleanSetting fastSpear = addSetting(new BooleanSetting("FastSpear", false));
    private final IntSetting attacks = addSetting(new IntSetting("Attacks", 1, 1, 10));
    private final IntSetting swapBackDelay = addSetting(new IntSetting("Swap back delay", 2, 1, 20));
    private final IntSetting cooldown = addSetting(new IntSetting("Cooldown", 0, 0, 10));
    private LocalPlayer swappedPlayer;
    private int originalSlot = -1;
    private int spearSlot = -1;
    private long ticks;
    private long restoreTick;
    private long nextSwapTick;
    private boolean attacked;

    public SpearSwapModule() {
        super("SpearSwap", "Spear the swap", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        nextSwapTick = ticks;
    }

    @Override
    public void tickInput(Minecraft client) {
        var player = client.player;
        if (!Gameplay.canAct(client) || client.gameMode == null
                || player.isUsingItem() || player.isHandsBusy()) {
            return;
        }
        if (originalSlot >= 0) {
            return;
        }
        if (SlotSelection.isOwned() || ticks < nextSwapTick) {
            return;
        }
        if (!fastSpear.get() && player.getAttackStrengthScale(0) < 1) {
            return;
        }

        int slot = findSpear(player);
        if (slot < 0) {
            if (bindMode().get() == BindModeSetting.Mode.TOGGLE) {
                setEnabled(false);
            }
            return;
        }
        var spear = player.getInventory().getItem(slot);
        var piercingWeapon = spear.get(DataComponents.PIERCING_WEAPON);
        if (piercingWeapon == null) {
            return;
        }
        var inventory = player.getInventory();
        if (!SlotSelection.begin(this, inventory)) {
            return;
        }
        swappedPlayer = player;
        originalSlot = inventory.getSelectedSlot();
        spearSlot = slot;
        if (!AttributeSwap.swap(client, slot)) {
            restore(client);
            return;
        }
        attacked = false;
        tryAttack(client);
    }

    private void tryAttack(Minecraft client) {
        var player = client.player;
        var spear = player.getMainHandItem();
        var piercingWeapon = spear.get(DataComponents.PIERCING_WEAPON);
        var lunge = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUNGE);
        if (piercingWeapon == null || !spear.is(ItemTags.SPEARS)
                || EnchantmentHelper.getItemEnchantmentLevel(lunge, spear) <= 0) {
            restore(client);
            return;
        }
        for (int attack = 0; attack < attacks.get(); attack++) {
            AttackUtils.piercingAttack(client);
        }
        attacked = true;
        restoreTick = ticks + swapBackDelay.get();
    }

    public void tickSwap(Minecraft client) {
        ticks++;
        if (originalSlot < 0) {
            return;
        }
        if (!isCurrentSwap(client) || !swappedPlayer.isAlive() || (attacked && ticks >= restoreTick)) {
            restore(client);
            if (bindMode().get() == BindModeSetting.Mode.TOGGLE) {
                setEnabled(false);
            }
        }
    }

    @Override
    protected void onDisable() {
        restore(Minecraft.getInstance());
    }

    private boolean isCurrentSwap(Minecraft client) {
        return client.player == swappedPlayer && client.level == swappedPlayer.level()
                && SlotSelection.owns(this, swappedPlayer.getInventory())
                && swappedPlayer.getInventory().getSelectedSlot() == spearSlot;
    }

    private void restore(Minecraft client) {
        if (originalSlot < 0) {
            return;
        }
        if (isCurrentSwap(client)) {
            AttributeSwap.swap(client, originalSlot);
            int repeatDelay = attacked && isEnabled() && bindMode().get() == BindModeSetting.Mode.HOLD
                    ? cooldown.get() : 0;
            nextSwapTick = ticks + Math.max(fastSpear.get() ? 1 : 0, repeatDelay);
        }
        SlotSelection.complete(this, -1);
        swappedPlayer = null;
        originalSlot = -1;
        spearSlot = -1;
        restoreTick = 0;
        attacked = false;
    }

    private static int findSpear(LocalPlayer player) {
        var lunge = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUNGE);
        return InventoryUtils.findHotbar(player.getInventory(), stack -> stack.is(ItemTags.SPEARS)
                && EnchantmentHelper.getItemEnchantmentLevel(lunge, stack) > 0);
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 170)
    private void onPreTick(PreTickEvent event) {
        tickSwap(event.client());
    }
}
