package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.AutoTotemModule;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerSilentInventoryMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void paradox$silentInventory(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != 0 && Paradox.MODULES.get(AutoTotemModule.class).silentBusy()) ci.cancel();
    }
}
