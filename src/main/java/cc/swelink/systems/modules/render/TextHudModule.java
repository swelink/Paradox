package cc.swelink.systems.modules.render;

import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.renderer.UiTokens;
import cc.swelink.settings.StringSetting;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.hud.HudPlacement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class TextHudModule extends Module implements PlacedHudElement {
    private static final float FONT_SIZE = UiTokens.TEXT_TITLE;
    private final StringSetting text = addSetting(new StringSetting("Text", "Paradox", 64));
    private final HudPlacement placement = new HudPlacement(0, 0.1f, false).withCenterSnap();
    private final HudPopAnimation enablePop = new HudPopAnimation();

    public TextHudModule() {
        super("TextHud", "Shows your own text", Category.RENDER);
    }

    @Override
    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if ((!isEnabled() && !enablePop.exiting()) || client.player == null || client.level == null
                || client.gui.hud.isHidden() || text.get().isBlank()) {
            placement.clearSize();
            stopDragging();
            return;
        }
        if (!HudPlacement.canDrag(client)) stopDragging();
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(placement.renderScale(client));
        try (var renderer = new Renderer2D(graphics)) {
            TextRenderer title = renderer.text.withFont(TextRenderer.Font.INTER_MEDIUM);
            float textWidth = title.measure(text.get(), FONT_SIZE).width();
            float height = title.lineHeight(FONT_SIZE);
            placement.updateAnchored(client, textWidth, height, height);
            float x = placement.x();
            float y = placement.y();
            enablePop.apply(renderer, x, y, textWidth, height, placement.touchedEdges());
            renderer.opacity(enablePop.value());
            title.draw(text.get(), x, y, FONT_SIZE, ThemeColors.text());
        } finally {
            pose.popMatrix();
        }
    }

    @Override
    public HudPlacement placement() {
        return placement;
    }

    @Override
    protected void onEnable() {
        enablePop.reset();
    }

    @Override
    protected void onDisable() {
        enablePop.close();
        stopDragging();
    }
}
