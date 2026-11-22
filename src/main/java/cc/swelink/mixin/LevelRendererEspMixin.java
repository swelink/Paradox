package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.esp.EspModule;
import cc.swelink.systems.modules.render.esp.mode.ShaderEsp;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererEspMixin {
    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;ENTITY_OUTLINE_POST_CHAIN_ID:Lnet/minecraft/resources/Identifier;"))
    private Identifier paradox$espShader(Identifier original) {
        return Paradox.MODULES.get(EspModule.class).shaderActive() ? ShaderEsp.POST_EFFECT : original;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"))
    private PostChain paradox$espUniforms(ShaderManager manager, Identifier id, Set<Identifier> targets, Operation<PostChain> original) {
        PostChain chain = original.call(manager, id, targets);
        if (ShaderEsp.POST_EFFECT.equals(id)) {
            Paradox.MODULES.get(EspModule.class).shader().configure(chain);
        }
        return chain;
    }
}
