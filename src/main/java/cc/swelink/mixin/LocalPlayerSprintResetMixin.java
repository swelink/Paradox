package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.SprintResetModule;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSprintResetMixin {
    @ModifyReturnValue(method = "canStartSprinting", at = @At("RETURN"))
    private boolean paradox$resetSprint(boolean original) {
        return original && !Paradox.MODULES.get(SprintResetModule.class).blocksSprint((LocalPlayer) (Object) this);
    }
}
