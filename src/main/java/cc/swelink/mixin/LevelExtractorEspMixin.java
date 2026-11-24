package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.esp.EspModule;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorEspMixin {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void paradox$includeHiddenPlayers(Entity entity, Frustum frustum, double cameraX, double cameraY,
                                            double cameraZ, float tick, long sectionFade,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (Paradox.MODULES.get(EspModule.class).glowActive() && Paradox.MODULES.get(EspModule.class).shouldRender(entity)) {
            cir.setReturnValue(frustum.isVisible(entity.getBoundingBox().inflate(0.5)));
        }
    }
}
