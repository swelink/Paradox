package cc.swelink.gui.screens;

import cc.swelink.renderer.Renderer2D;
import cc.swelink.systems.hud.HudElement;
import cc.swelink.systems.hud.HudOrder;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.ModuleManager;


import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import cc.swelink.utils.hud.HudPlacement;

import java.util.function.IntSupplier;

public final class HudChatOverlay extends Screen {
    private final ModuleManager manager;
    private final IntSupplier accent;
    private HudElement dragging;

    public HudChatOverlay(ModuleManager manager, IntSupplier accent) {
        super(Component.literal("HUD"));
        this.manager = manager;
        this.accent = accent;
    }

    boolean draggingHud() {
        return dragging != null;
    }

    private boolean editable(Module module) {
        return module.isEnabled() && module instanceof HudElement
                && (!(module instanceof PlacedHudElement placed) || placed.visibleForDragging());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.player == null || minecraft.gui.hud.isHidden()) return;
        float scale = HudPlacement.scale(minecraft);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale);
        try (var renderer = new Renderer2D(graphics)) {
            HudElement selected = dragging != null ? dragging : topAt(mouseX, mouseY);
            if (selected != null) outline(renderer, selected, scale);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (minecraft.player == null || minecraft.gui.hud.isHidden()) return false;
        HudElement hud = topAt(event.x(), event.y());
        if (hud == null || !hud.mouseClicked(event)) return false;
        dragging = hud;
        HudOrder.raise(hud);
        return true;
    }

    private HudElement topAt(double x, double y) {
        var ordered = HudOrder.ordered(manager.modules());
        for (int i = ordered.size() - 1; i >= 0; i--) {
            HudElement hud = ordered.get(i);
            if (hud instanceof Module module && editable(module) && contains(hud, x, y)) return hud;
        }
        return null;
    }

    private static boolean contains(HudElement hud, double x, double y) {
        return hud.editorBounds().contains(x, y);
    }

    private void outline(Renderer2D renderer, HudElement hud, float scale) {
        var bounds = hud.editorBounds();
        float left = bounds.x() / scale;
        float top = bounds.y() / scale;
        float right = (bounds.x() + bounds.width()) / scale;
        float bottom = (bounds.y() + bounds.height()) / scale;
        int color = accent.getAsInt();
        if (!(hud instanceof PlacedHudElement placed)) {
            renderer.sdf.dockedBorder(left, top, right - left, bottom - top, 0, 1, color, 0);
            return;
        }
        var placement = placed.placement();
        float radius = Math.min(placed.cornerRadius() * placement.currentScale() / scale,
                Math.min(right - left, bottom - top) / 2);
        renderer.sdf.dockedBorder(left, top, right - left, bottom - top, radius, 1, color, 0);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging != null) return dragging.mouseDragged(event);
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (dragging instanceof PlacedHudElement placed) {
            placed.placement().resize(vertical);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null && dragging.mouseReleased(event)) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void removed() {
        if (dragging != null) dragging.stopDragging();
        dragging = null;
        for (Module module : manager.modules()) {
            if (module instanceof HudElement hud) hud.stopDragging();
        }
        super.removed();
    }
}
