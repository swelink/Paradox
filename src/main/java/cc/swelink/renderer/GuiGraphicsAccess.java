package cc.swelink.renderer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

public interface GuiGraphicsAccess {
    static GuiGraphicsAccess of(GuiGraphicsExtractor graphics) {
        return (GuiGraphicsAccess) (Object) graphics;
    }

    GuiRenderState paradox$renderState();

    ScreenRectangle paradox$scissorArea();

    void paradox$pushScissor(ScreenRectangle rectangle);
}
