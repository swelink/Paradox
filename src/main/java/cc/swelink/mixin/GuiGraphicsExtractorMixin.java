package cc.swelink.mixin;

import cc.swelink.renderer.GuiGraphicsAccess;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements GuiGraphicsAccess {
    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    @Unique
    private ScissorStackInvoker paradox$scissors;

    @ModifyExpressionValue(method = "<init>", at = @At(value = "NEW",
            target = "net/minecraft/client/gui/GuiGraphicsExtractor$ScissorStack"))
    private @Coerce Object paradox$captureScissors(@Coerce Object scissors) {
        paradox$scissors = (ScissorStackInvoker) scissors;
        return scissors;
    }

    @Override
    public GuiRenderState paradox$renderState() {
        return guiRenderState;
    }

    @Override
    public ScreenRectangle paradox$scissorArea() {
        return paradox$scissors.paradox$peek();
    }

    @Override
    public void paradox$pushScissor(ScreenRectangle rectangle) {
        paradox$scissors.paradox$push(rectangle);
    }
}
