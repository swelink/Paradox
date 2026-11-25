package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.TargetRefreshEvent;
import cc.swelink.systems.modules.combat.AutoTotemModule;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftCombatInputMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void paradox$manualInteraction(CallbackInfo ci) {
        var client = (Minecraft) (Object) this;
        if (Paradox.EVENTS.post(new ManualUseEvent(client)).cancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void paradox$silentInventory(CallbackInfo ci) {
        if (Paradox.MODULES.get(AutoTotemModule.class).blocksActions()) ci.cancel();
    }

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0))
    private void paradox$combatInteractions(CallbackInfo ci) {
        var client = (Minecraft) (Object) this;
        Paradox.EVENTS.post(new TargetRefreshEvent(client));
        Paradox.MODULES.tickInput(client);
    }
}
