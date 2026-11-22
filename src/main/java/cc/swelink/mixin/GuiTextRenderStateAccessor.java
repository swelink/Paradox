package cc.swelink.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiTextRenderState.class)
public interface GuiTextRenderStateAccessor {
    @Accessor("font")
    Font paradox$font();

    @Accessor("text")
    FormattedCharSequence paradox$text();

    @Accessor("x")
    int paradox$x();

    @Accessor("y")
    int paradox$y();

    @Accessor("color")
    int paradox$color();

    @Accessor("backgroundColor")
    int paradox$backgroundColor();
}
