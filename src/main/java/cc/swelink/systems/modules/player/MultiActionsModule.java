package cc.swelink.systems.modules.player;

import cc.swelink.mixin.MinecraftAttackInvoker;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.Gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.HitResult;

public final class MultiActionsModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Grim", "Grim", "Custom"));
    private final BooleanSetting shield = addSetting(new BooleanSetting("Shield", true));
    private final BooleanSetting eat = addSetting(new BooleanSetting("Eat", true));

    public MultiActionsModule() {
        super("MultiActions", "Hits while eating or blocking", Category.PLAYER);
    }

    @Override
    public void tickInput(Minecraft client) {
        var player = client.player;
        if (player == null || client.gameMode == null || Gameplay.inMenu(client) || player.isSpectator()) return;
        if (!player.isUsingItem() || !applies(player) || player.isHandsBusy()) return;
        if (!client.options.keyAttack.consumeClick()) return;
        while (client.options.keyAttack.consumeClick()) {
        }
        if (mode.get().equals("Custom")) {
            ((MinecraftAttackInvoker) client).paradox$attack();
            return;
        }
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) return;
        attackWhileUsing(client, ((MinecraftAttackInvoker) client)::paradox$attack);
    }

    public boolean attackWhileUsing(Minecraft client, Runnable attack) {
        var player = client.player;
        if (!isEnabled() || !mode.get().equals("Grim") || player == null || !player.isUsingItem() || !applies(player)) {
            return false;
        }
        var hand = player.getUsedItemHand();
        client.gameMode.releaseUsingItem(player);
        attack.run();
        client.gameMode.useItem(player, hand);
        return true;
    }

    public boolean holdsWhileAttacking(LocalPlayer player) {
        return isEnabled() && player.isUsingItem() && applies(player) && mode.get().equals("Custom");
    }

    private boolean applies(LocalPlayer player) {
        var stack = player.getUseItem();
        if (stack.has(DataComponents.BLOCKS_ATTACKS)) return shield.get();
        if (stack.has(DataComponents.CONSUMABLE)) return eat.get();
        return false;
    }
}
