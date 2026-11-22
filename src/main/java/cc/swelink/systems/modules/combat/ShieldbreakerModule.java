package cc.swelink.systems.modules.combat;

import cc.swelink.events.AttackEvent;
import cc.swelink.events.EventHandler;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.ShieldBlockUtils;
import cc.swelink.utils.player.AttributeSwap;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;

public final class ShieldbreakerModule extends Module {
    private final IntSetting swapBackDelay = addSetting(new IntSetting("Swap back delay", 2, 1, 20));
    private LocalPlayer swappedPlayer;
    private int originalSlot = -1;
    private int axeSlot = -1;
    private long ticks;
    private long restoreTick;

    public ShieldbreakerModule() {
        super("ShieldBreaker", "Breaks shields with an axe", Category.COMBAT);
    }

    public void beforeAttack(Minecraft client, int entityId) {
        if (SlotSelection.isOwned()) {
            return;
        }
        var player = client.player;
        if (!isEnabled() || player == null || client.level == null || client.gameMode == null || player.isSpectator()) {
            return;
        }
        var entity = client.level.getEntity(entityId);
        if (!(entity instanceof Player target) || target == player || !target.isAlive()
                || !ShieldBlockUtils.wouldBlockAttack(client, target)) {
            return;
        }

        validateSwap(client);
        var inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (inventory.getItem(selected).is(ItemTags.AXES)) {
            if (originalSlot >= 0) {
                restoreTick = ticks + swapBackDelay.get();
            }
            return;
        }

        int axe = InventoryUtils.findHotbar(player.getInventory(), stack -> stack.is(ItemTags.AXES));
        if (axe < 0 || !AttributeSwap.swap(client, axe)) {
            return;
        }
        swappedPlayer = player;
        originalSlot = selected;
        axeSlot = axe;
        restoreTick = ticks + swapBackDelay.get();
    }

    public void tickSwap(Minecraft client) {
        ticks++;
        validateSwap(client);
        if (originalSlot >= 0 && ticks >= restoreTick && !SlotSelection.isOwned()) {
            restore(client);
        }
    }

    private void validateSwap(Minecraft client) {
        if (originalSlot < 0) {
            return;
        }
        if (client.player != swappedPlayer || client.level != swappedPlayer.level()
                || swappedPlayer.getInventory().getSelectedSlot() != axeSlot) {
            clearSwap();
        }
    }

    @Override
    protected void onDisable() {
        restoreTick = 0;
    }

    private void restore(Minecraft client) {
        if (!AttributeSwap.swap(client, originalSlot)) {
            return;
        }
        clearSwap();
    }

    private void clearSwap() {
        swappedPlayer = null;
        originalSlot = -1;
        axeSlot = -1;
        restoreTick = 0;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 160)
    private void onPreTick(PreTickEvent event) {
        tickSwap(event.client());
    }

    @EventHandler(priority = 60)
    private void onAttack(AttackEvent event) {
        beforeAttack(event.client(), event.entityId());
    }
}
