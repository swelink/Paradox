package cc.swelink.systems.modules.render;

import cc.swelink.Paradox;
import cc.swelink.gui.screens.MenuText;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.Glass;
import cc.swelink.renderer.HudBackdrop;
import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.renderer.PixelSnap;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.hud.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public final class InventoryHudModule extends Module implements PlacedHudElement {
    private static final int COLUMNS = 9;
    private static final int ROWS = 3;
    private static final float SLOT = 20;
    private static final float ITEM_SIZE = 16;
    private static final float SLOT_GAP = 2;
    private static final float PADDING = 7;
    private static final float WIDTH = COLUMNS * SLOT + (COLUMNS - 1) * SLOT_GAP + PADDING * 2;
    private static final float HEADER_HEIGHT = 28;
    private static final float HEIGHT = HEADER_HEIGHT + ROWS * SLOT + (ROWS - 1) * SLOT_GAP + PADDING * 2;
    private static final float ICON_SIZE = 18;
    private static final float ICON_GAP = 7;
    private static final float RADIUS = 10;
    private final BooleanSetting shadows = addSetting(new BooleanSetting("Shadows", true));
    private final HudPlacement placement = new HudPlacement(1, 0.2f, false).withCenterSnap();
    private final SpringAnimation visibility = new SpringAnimation();
    private final FrameClock clock = new FrameClock();

    public InventoryHudModule() {
        super("InventoryHud", "Shows your inventory", Category.RENDER);
    }

    @Override
    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if ((!isEnabled() && visibility.value() == 0) || client.player == null || client.level == null || client.gui.hud.isHidden()) {
            hide();
            return;
        }
        if (!HudPlacement.canDrag(client) || !isEnabled()) stopDragging();
        float delta = clock.nextDelta();
        float reveal = Math.clamp(visibility.update(isEnabled() ? 1 : 0, 20, 0.85f, delta), 0, 1);
        if (!isEnabled() && reveal <= 0.001f) {
            hide();
            return;
        }
        var inventory = client.player.getInventory();
        var pose = graphics.pose();
        pose.pushMatrix();
        float scale = placement.renderScale(client);
        pose.scale(scale);
        try {
            placement.updateAnchored(client, WIDTH, HEIGHT, HEADER_HEIGHT);
            float x = placement.x();
            float y = placement.y();
            HudPopAnimation.pop(pose, reveal, x, y, WIDTH, HEIGHT, placement.touchedEdges());
            try (var renderer = new Renderer2D(graphics)) {
                renderer.opacity(reveal);
                int edges = placement.touchedEdges();
                if (shadows.get()) HudBackdrop.draw(renderer, List.of(HudBackdrop.docked(x, y, WIDTH, HEIGHT, RADIUS, edges)));
                renderer.blur.frostedGlassWithHeader(renderer, Glass.HUD, x, y, WIDTH, HEIGHT, RADIUS, ThemeColors.hudPanel(), HEADER_HEIGHT,
                        ThemeColors.hudHeader(), edges);
                renderer.textures.icon(Paradox.id("icons/inventory.png"), x + PADDING,
                        y + (HEADER_HEIGHT - ICON_SIZE) / 2, ICON_SIZE, AccentWave.text());
                var title = renderer.text.withFont(TextRenderer.Font.INTER_MEDIUM);
                title.draw("Inventory", x + PADDING + ICON_SIZE + ICON_GAP,
                        y + (HEADER_HEIGHT - title.lineHeight(15)) / 2, 15, ThemeColors.text());
                for (int i = 0; i < COLUMNS * ROWS; i++) {
                    renderer.sdf.roundedRect(slotX(x, i), slotY(y, i), SLOT, SLOT, 5, ThemeColors.hudHeader());
                }
            }
            float grow = Math.clamp((reveal - 0.25f) / 0.75f, 0, 1);
            float itemScale = grow * grow * (3 - 2 * grow);
            float pixelRatio = PixelSnap.ratio(scale, ITEM_SIZE);
            if (itemScale > 0.01f) {
                graphics.nextStratum();
                for (int i = 0; i < COLUMNS * ROWS; i++) {
                    var stack = inventory.getItem(COLUMNS + i);
                    if (stack.isEmpty()) continue;
                    float itemX = slotX(x, i) + (SLOT - ITEM_SIZE) / 2;
                    float itemY = slotY(y, i) + (SLOT - ITEM_SIZE) / 2;
                    if (itemScale >= 0.999f) {
                        PixelSnap.push(graphics, itemX, itemY, ITEM_SIZE);
                    } else {
                        pose.pushMatrix();
                        pose.translate(itemX + ITEM_SIZE / 2, itemY + ITEM_SIZE / 2);
                        pose.scale(itemScale * pixelRatio);
                        pose.translate(-ITEM_SIZE / 2, -ITEM_SIZE / 2);
                    }
                    graphics.item(client.player, stack, 0, 0, i);
                    MenuText.enable(graphics);
                    graphics.itemDecorations(client.font, stack, 0, 0);
                    MenuText.end();
                    pose.popMatrix();
                }
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static float slotX(float x, int index) {
        return x + PADDING + index % COLUMNS * (SLOT + SLOT_GAP);
    }

    private static float slotY(float y, int index) {
        return y + HEADER_HEIGHT + PADDING + index / COLUMNS * (SLOT + SLOT_GAP);
    }

    private void hide() {
        visibility.reset(0);
        placement.clearSize();
        stopDragging();
        clock.reset();
    }

    @Override
    public float cornerRadius() {
        return RADIUS;
    }

    @Override
    public HudPlacement placement() {
        return placement;
    }

    @Override
    public boolean visibleForDragging() {
        return isEnabled() && visibility.value() > 0.001f;
    }

    @Override
    protected void onEnable() {
        visibility.reset(0);
        clock.reset();
    }

    @Override
    protected void onDisable() {
        stopDragging();
    }
}
