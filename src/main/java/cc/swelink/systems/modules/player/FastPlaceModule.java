package cc.swelink.systems.modules.player;

import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;

public final class FastPlaceModule extends Module {
    private final IntSetting delay = addSetting(new IntSetting("Delay", 1, 1, 3) {
        @Override
        protected String formatValue(int value) {
            return value == 1 ? "1 tick" : value + " ticks";
        }
    });
    private final BooleanSetting blocksOnly = addSetting(new BooleanSetting("Blocks only", true));

    public FastPlaceModule() {
        super("FastPlace", "Places blocks faster", Category.PLAYER);
    }

    public int delay(LocalPlayer player, int vanilla) {
        if (!isEnabled() || player == null) return vanilla;
        if (blocksOnly.get() && !holdingBlock(player)) return vanilla;
        return Math.min(vanilla, delay.get());
    }

    private static boolean holdingBlock(LocalPlayer player) {
        for (var hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof BlockItem) return true;
        }
        return false;
    }
}
