package cc.swelink.renderer;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import org.joml.Quaternionf;

public record EspPreviewRenderState(EntityRenderState entity, Quaternionf rotation,
                                    int x0, int y0, int x1, int y1, float scale, float opacity,
                                    boolean shader, int color, int outlineWidth, float fill, int glow, int shape, int waveMode,
                                    ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
}
