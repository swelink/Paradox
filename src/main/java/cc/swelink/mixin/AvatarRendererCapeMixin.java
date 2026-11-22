package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.CapeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererCapeMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void paradox$wearCape(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        var module = Paradox.MODULES.get(CapeModule.class);
        if (module.isEnabled() && avatar == Minecraft.getInstance().player) {
            module.apply(state);
        }
    }
}
