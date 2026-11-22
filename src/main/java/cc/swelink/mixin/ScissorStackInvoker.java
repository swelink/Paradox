package cc.swelink.mixin;

import net.minecraft.client.gui.navigation.ScreenRectangle;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.GuiGraphicsExtractor$ScissorStack")
public interface ScissorStackInvoker {
    @Invoker("peek")
    ScreenRectangle paradox$peek();

    @Invoker("push")
    void paradox$push(ScreenRectangle rectangle);
}
