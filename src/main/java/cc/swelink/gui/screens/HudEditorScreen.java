package cc.swelink.gui.screens;

import cc.swelink.Paradox;
import cc.swelink.renderer.GuiViewport;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.systems.modules.ModuleManager;
import cc.swelink.systems.modules.client.ClickGuiModule;
import cc.swelink.utils.hud.HudPlacement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.function.IntSupplier;


public final class HudEditorScreen extends Screen {
    private final ModuleManager manager;
    private final IntSupplier accent;
    private final HudChatOverlay overlay;
    private static HudModulesMenu menu;
    private final Screen returnTo;
    private final boolean switched;
    private boolean menuDragging;

    public HudEditorScreen(ModuleManager manager, IntSupplier accent, Screen returnTo, boolean switched) {
        super(Component.literal("HUD Editor"));
        this.switched = switched;
        this.manager = manager;
        this.accent = accent;
        this.returnTo = returnTo;
        overlay = new HudChatOverlay(manager, accent);
        if (menu == null) menu = new HudModulesMenu(manager, accent);
    }

    public Screen returnTo() {
        return returnTo;
    }

    @Override
    protected void init() {
        overlay.init(width, height);
        if (switched) menu.carryWindow();
        menu.init(width, height);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        overlay.resize(width, height);
        menu.resize(width, height);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) extractPanorama(graphics, partialTick);
    }

    public static void drawGrid(Minecraft client, GuiGraphicsExtractor graphics) {
        float scale = HudPlacement.scale(client);
        var viewport = GuiViewport.atScale(client, scale);
        float line = 1 / Math.max(client.getWindow().getGuiScale() * scale, 0.0001f);
        int minor = ARGB.color(18, 255, 255, 255);
        int major = ARGB.color(40, Paradox.MODULES.get(ClickGuiModule.class).accentColor());
        int columns = (int) Math.ceil(viewport.width() / HudPlacement.GRID);
        int rows = (int) Math.ceil(viewport.height() / HudPlacement.GRID);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale);
        try (Renderer2D renderer = new Renderer2D(graphics)) {
            for (int i = 1; i < columns; i++) {
                renderer.fill(i * HudPlacement.GRID, 0, line, viewport.height(), i % 4 == 0 ? major : minor);
            }
            for (int i = 1; i < rows; i++) {
                renderer.fill(0, i * HudPlacement.GRID, viewport.width(), line, i % 4 == 0 ? major : minor);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean overMenu = menuDragging || menu.contains(mouseX, mouseY);
        if (!overMenu || overlay.draggingHud()) overlay.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();
        menu.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.contains(event.x(), event.y())) {
            menuDragging = true;
            menu.mouseClicked(event, doubleClick);
            return true;
        }
        if (overlay.mouseClicked(event, doubleClick)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (menuDragging) return menu.mouseDragged(event, deltaX, deltaY);
        return overlay.mouseDragged(event, deltaX, deltaY) || super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (menuDragging) {
            menuDragging = false;
            return menu.mouseReleased(event);
        }
        return overlay.mouseReleased(event) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (menu.contains(mouseX, mouseY)) return menu.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        return overlay.mouseScrolled(mouseX, mouseY, horizontal, vertical)
                || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() != InputConstants.KEY_ESCAPE && menu.keyPressed(event)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return menu.charTyped(event) || super.charTyped(event);
    }

    @Override
    public void tick() {
        super.tick();
        menu.tick();
    }

    @Override
    public void removed() {
        overlay.removed();
        menu.removed();
        super.removed();
    }

    @Override
    public void onClose() {
        if (switched) manager.get(ClickGuiModule.class).returnFromHudEditor();
        else exit();
    }

    public void exit() {
        minecraft.gui.setScreen(returnTo);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
