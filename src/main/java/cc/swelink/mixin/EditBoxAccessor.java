package cc.swelink.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Invoker("applyFormat")
    FormattedCharSequence paradox$applyFormat(String text, int offset);

    @Accessor("suggestion")
    String paradox$suggestion();
}
