package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.NametagsModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererNametagsMixin {
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void paradox$replaceNametags(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                      CameraRenderState camera, CallbackInfo ci) {
        if (Paradox.MODULES.get(NametagsModule.class).replaces(net.minecraft.world.entity.EntityTypes.PLAYER)) ci.cancel();
    }
}
