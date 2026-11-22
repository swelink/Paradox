package cc.swelink.renderer;

import net.minecraft.client.Minecraft;

public record GuiViewport(float width, float height) {
    public static GuiViewport atScale(Minecraft client, float scale) {
        var window = client.getWindow();
        float pixelsPerUnit = window.getGuiScale() * scale;
        return new GuiViewport(window.getWidth() / pixelsPerUnit, window.getHeight() / pixelsPerUnit);
    }
}
