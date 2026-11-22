package cc.swelink.systems.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public interface HudElement {
    void render(Minecraft client, GuiGraphicsExtractor graphics);

    Bounds editorBounds();

    boolean mouseClicked(MouseButtonEvent event);

    boolean mouseDragged(MouseButtonEvent event);

    boolean mouseReleased(MouseButtonEvent event);

    void stopDragging();

    record Bounds(float x, float y, float width, float height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
