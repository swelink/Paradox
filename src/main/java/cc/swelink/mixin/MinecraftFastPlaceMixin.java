package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.player.FastPlaceModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftFastPlaceMixin {
    @Shadow public LocalPlayer player;

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "CONSTANT", args = "intValue=4"))
    private int paradox$fastPlace(int delay) {
        return Paradox.MODULES.get(FastPlaceModule.class).delay(player, delay);
    }
}
