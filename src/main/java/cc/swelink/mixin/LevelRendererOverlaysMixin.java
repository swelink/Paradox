package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.renderer.CombatLagRenderer;
import cc.swelink.renderer.TargetEspRenderer;
import cc.swelink.renderer.TargetStrafeRingRenderer;
import cc.swelink.renderer.TrajectoryRenderer;
import cc.swelink.systems.modules.movement.TargetStrafeModule;
import cc.swelink.systems.modules.render.PlaceModule;
import cc.swelink.systems.modules.render.TargetEspModule;
import cc.swelink.systems.modules.render.TrajectoryModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererOverlaysMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void paradox$submitOverlays(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        TrajectoryRenderer.submit(Paradox.MODULES.get(TrajectoryModule.class), pose, state, collector);
        TargetStrafeRingRenderer.submit(Paradox.MODULES.get(TargetStrafeModule.class), pose, state, collector);
        TargetEspRenderer.submit(Paradox.MODULES.get(TargetEspModule.class), pose, state, collector);
        CombatLagRenderer.submit(pose, state, collector);
        Paradox.MODULES.get(PlaceModule.class).submit(pose, state, collector);
    }
}
