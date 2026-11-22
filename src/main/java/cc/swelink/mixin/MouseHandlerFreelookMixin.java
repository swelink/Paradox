package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.FreecamModule;
import cc.swelink.systems.modules.render.FreelookModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerFreelookMixin {
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void paradox$turnFreelook(LocalPlayer player, double horizontal, double vertical, Operation<Void> original) {
        if (!Paradox.MODULES.get(FreecamModule.class).turn(horizontal, vertical)
                && !Paradox.MODULES.get(FreelookModule.class).turn(horizontal, vertical)) {
            original.call(player, horizontal, vertical);
        }
    }
}
