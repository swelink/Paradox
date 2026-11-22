package cc.swelink.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public interface PreparedFrameInvoker {
    @Invoker("executePhase")
    void paradox$executePhase(FeatureRenderPhase<?> phase, FeatureFrameContext context, RenderPass pass);
}
