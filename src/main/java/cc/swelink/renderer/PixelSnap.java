package cc.swelink.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.joml.Vector2f;

public final class PixelSnap {
    private PixelSnap() {
    }

    public static float ratio(float scale, float size) {
        float pixels = Minecraft.getInstance().getWindow().getGuiScale() * scale * size;
        if (!(pixels > 0) || !Float.isFinite(pixels)) return 1;
        return Math.max(1, Math.round(pixels)) / pixels;
    }

    public static void push(GuiGraphicsExtractor graphics, float x, float y, float size) {
        var pose = graphics.pose();
        float guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        float scale = (float) Math.sqrt(Math.abs(pose.m00() * pose.m11() - pose.m01() * pose.m10()));
        float pixels = guiScale * scale;
        var center = pose.transformPosition(x + size / 2, y + size / 2, new Vector2f());
        pose.pushMatrix();
        if (!(pixels > 0) || !Float.isFinite(pixels) || pose.m01() != 0 || pose.m10() != 0) {
            pose.translate(x, y);
            return;
        }
        float fitted = Math.max(1, Math.round(pixels * size)) / (size * guiScale);
        float left = Math.round((center.x - size / 2 * fitted) * guiScale) / guiScale;
        float top = Math.round((center.y - size / 2 * fitted) * guiScale) / guiScale;
        pose.identity().translate(left, top).scale(fitted);
    }
}
