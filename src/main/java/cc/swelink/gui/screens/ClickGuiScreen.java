package cc.swelink.gui.screens;

import static cc.swelink.gui.screens.ClickGuiDraw.clip;
import static cc.swelink.gui.screens.ClickGuiDraw.drawExpandIcon;
import static cc.swelink.gui.screens.ClickGuiDraw.fitLabel;
import static cc.swelink.gui.screens.ClickGuiDraw.inside;
import static cc.swelink.gui.screens.ClickGuiDraw.label;
import static cc.swelink.gui.screens.ClickGuiDraw.mix;

import cc.swelink.Paradox;
import cc.swelink.gui.previews.EspPlayerPreview;
import cc.swelink.systems.modules.combat.AutoAnchorModule;
import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.renderer.HudBackdrop;
import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.renderer.GuiViewport;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.UniformGuiFade;
import cc.swelink.renderer.UiScale;
import cc.swelink.settings.ActionSetting;
import cc.swelink.settings.BindSetting;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.ChoiceSetting;
import cc.swelink.settings.ColorSetting;
import cc.swelink.settings.PlayerPreviewSetting;
import cc.swelink.settings.Setting;
import cc.swelink.settings.SliderSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.hud.HudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.client.ClickGuiModule;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.ModuleManager;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import cc.swelink.systems.modules.render.DynamicIslandModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import net.minecraft.util.ARGB;

public class ClickGuiScreen extends Screen {
    private static final float BRAND_LOGO_HEIGHT = 21;
    private static final String BRAND_TEXT = "Paradox";
    private static final String BRAND_VERSION = "b1.0";
    private static final float BRAND_FONT_SIZE = 18.5f;
    private static final float BRAND_VERSION_SIZE = 9;
    private static final float BRAND_CAP_HEIGHT = 0.73f;
    private static final Category[] MODULE_CATEGORIES = {Category.COMBAT, Category.PLAYER, Category.RENDER, Category.MOVEMENT, Category.CLIENT};
    private static final float WINDOW_WIDTH = 510;
    private static final float WINDOW_HEIGHT = 320;
    private static final float TITLE_HEIGHT = 50;
    private static final float TAB_DROP = 6;
    private static final float SIDEBAR_ICON = 16;
    private static final float SIDEBAR_EDGE = 6;
    private static final float SIDEBAR_GAP = 6;
    private static final float SIDEBAR_EXTRA = 12;
    private static final float CONTENT_X = 7;
    private static final float CONTENT_PAD_Y = 7;
    private static final float COLUMN_GAP = 0;
    private static final float CARD_INSET = 3;
    private static final float VIEW_INSET = CONTENT_PAD_Y + CARD_INSET;
    private static final float CATEGORY_HEIGHT = ClickGuiStyle.ROW_HEIGHT;
    private final float[][] tabRects = new float[5][];
    private final SpringAnimation highlightY = new SpringAnimation(-1);
    private final SpringAnimation highlightWidth = new SpringAnimation(-1);
    private final SpringAnimation descriptionReveal = new SpringAnimation();
    private boolean describing;
    private Module describedModule;
    private Module shownDescription;
    private static final float DESCRIPTION_FONT = 12;
    private static final float DESCRIPTION_PAD = 7;
    private static final float DESCRIPTION_WIDTH = 190;
    private final TextRenderer measureText = new Renderer2D(null).text;
    private float sidebarWidth;
    private Panel previousCategory;
    private float switchProgress = 1;
    private static final float SWITCH_DURATION = 0.24f;
    private Category selectedCategory = Category.CLIENT;
    private final Map<Category, Float> categoryScroll = new EnumMap<>(Category.class);
    private static float windowX = Float.NaN;
    private static float windowY;
    private boolean draggingWindow;
    private ConfigPanel configPanel;
    private static float resizeScale = 1;
    private static float targetResizeScale = 1;
    private final SpringAnimation windowPop = new SpringAnimation();
    private boolean closing;
    private Screen returnTo;
    private Screen cleanViewBackground;
    private static final float POP_SPEED = 38;
    private static final float CLOSED = 0.03f;
    private static final float FADE_SPEED = 44;

    private float windowZoom() {
        return 0.9f + 0.1f * windowPop.value();
    }

    private float windowOpacity() {
        return FrameClock.smoothStep(Math.clamp(windowPop.value(), 0, 1));
    }

    private float dimOpacity() {
        return windowOpacity();
    }

    private float categoryFade = 1;
    private final Map<Category, SpringAnimation> categorySelection = new java.util.EnumMap<>(Category.class);
    private final Map<Category, SpringAnimation> tabHover = new java.util.EnumMap<>(Category.class);
    private EditBox sliderInput;
    private IntSetting editingSlider;
    private float sliderClickX = Float.NaN;
    private float sliderInputLeft;
    private float sliderInputRight;
    private float sliderInputCenterY;
    private long sliderEditedAt;
    private boolean fadeIn;
    private float switchFade = 1;

    private float panelWidth() { return columnWidth(); }
    private float panelSpan() { return contentWidth(); }
    private float contentWidth() { return WINDOW_WIDTH - sidebarWidth() - CONTENT_X * 2; }
    private float columnWidth() { return contentWidth(); }
    private float colorFieldWidth() { return panelWidth() - settingInset() * 2; }
    private static final float SCREEN_MARGIN = 0;
    private static final float PANEL_RADIUS = 18.4f;
    private static final float CONTENT_INSET = ClickGuiStyle.CONTENT_INSET;
    private static final float SETTING_INSET = 18;
    private static final float SLIDER_INSET = 26;
    private static final float SLIDER_KNOB_RADIUS = 5.5f;
    private static final float SLIDER_TRACK_Y = 31;
    private static final float SLIDER_TRACK_HEIGHT = 5;
    private static final float SETTINGS_SPACING = ClickGuiStyle.SETTINGS_SPACING;
    private static final float SETTING_CARD_INSET = 8;
    private static final float PAGE_FONT_SIZE = 15;
    private float settingFont = ClickGuiStyle.SETTING_FONT_SIZE;
    private static final float ROW_HEIGHT = CATEGORY_HEIGHT;
    private static final float SETTING_ROW_HEIGHT = ClickGuiStyle.SETTING_ROW_HEIGHT;
    private static final float SLIDER_HEIGHT = ClickGuiStyle.SLIDER_HEIGHT;
    private static final float SETTING_TEXT_OFFSET = 0;
    private static final float FONT_SIZE = ClickGuiStyle.FONT_SIZE;
    private static final float MODULE_FONT_SIZE = ClickGuiStyle.MODULE_FONT_SIZE;
    private static final float PICKER_HEIGHT = ClickGuiStyle.PICKER_HEIGHT;
    private static final float PICKER_RADIUS = 6.9f;
    private static final float COLOR_FIELD_HEIGHT = 80;
    private static final float HUE_GAP = 8;
    private static final float HUE_HEIGHT = 8;

    private final ModuleManager manager;
    private final boolean hudOnly;
    private final boolean configOnly;
    private final List<Panel> panels = new ArrayList<>();
    private final Set<Module> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Setting<?>> expandedModes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final FrameClock clock = new FrameClock();
    private final Map<Module, ModuleAnimation> moduleAnimations = new IdentityHashMap<>();
    private final Map<Setting<?>, SettingAnimation> settingAnimations = new IdentityHashMap<>();
    private float deltaTime;
    private final Module owner;
    private final IntSupplier accent;
    private final Set<ColorSetting> expandedColors = Collections.newSetFromMap(new IdentityHashMap<>());
    private ColorSetting draggedColor;
    private Panel colorPanel;
    private boolean draggingHue;
    private BindSetting binding;
    private int capturedBindKey = -1;
    private boolean open;
    private Panel draggedPanel;
    private float dragX;
    private float dragY;
    private SliderSetting draggedSlider;
    private Panel sliderPanel;
    private float sliderX;
    private float colorX;
    private final Map<PlayerPreviewSetting, EspPlayerPreview> playerPreviews = new IdentityHashMap<>();
    private PlayerPreviewSetting draggedPreview;

    public ClickGuiScreen(ModuleManager manager, Module owner, IntSupplier accent) {
        this(manager, owner, accent, false);
    }

    protected ClickGuiScreen(ModuleManager manager, Module owner, IntSupplier accent, boolean hudOnly) {
        this(manager, owner, accent, hudOnly, false);
    }

    protected ClickGuiScreen(ModuleManager manager, Module owner, IntSupplier accent, boolean hudOnly, boolean configOnly) {
        super(Component.literal("Paradox"));
        this.hudOnly = hudOnly;
        this.configOnly = configOnly;
        if (configOnly) selectedCategory = Category.CONFIG;
        this.manager = manager;
        this.owner = owner;
        this.accent = accent;
    }

    @Override
    protected void init() {
        manager.get(AutoAnchorModule.class).cancelForScreen();
        if (configPanel == null) configPanel = new ConfigPanel(minecraft);
        else configPanel.refresh();
        if (sliderInput == null) {
            sliderInput = new EditBox(minecraft.font, Component.literal("Slider value"));
            sliderInput.setMaxLength(12);
        }
        boolean opening = !open;
        if (opening) {
            tickClosedAnimations();
        }
        open = true;
        if (opening) {
            closing = false;
            capturedBindKey = -1;
            windowPop.reset(fadeIn ? 1 : 0);
        }

        categoryFade = fadeIn ? 0 : 1;
        switchFade = fadeIn ? 0 : 1;
        fadeIn = false;
        layoutWindow();
        if (panels.isEmpty()) panels.add(new Panel(selectedCategory, contentX(), windowY + panelTop()));
        positionContent();

        for (Panel panel : panels) {
            clampPanel(panel);
            panel.x = panel.targetX;
            panel.y = panel.targetY;
            panel.xMotion.reset(panel.x);
            panel.yMotion.reset(panel.y);
        }

        clock.reset();
        if (configPanel != null) configPanel.resetClock();
    }

    protected float scale() {
        float preferred = UiScale.of(minecraft) * resizeScale;
        return Math.min(preferred, fitScale(minecraft));
    }

    public static float fitScale(Minecraft client) {
        GuiViewport viewport = GuiViewport.atScale(client, 1);
        return Math.min(viewport.width() / (WINDOW_WIDTH + 24), viewport.height() / (WINDOW_HEIGHT + 24));
    }

    private float screenWidth() {
        return GuiViewport.atScale(minecraft, scale()).width();
    }

    private float screenHeight() {
        return GuiViewport.atScale(minecraft, scale()).height();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) extractPanorama(graphics, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        deltaTime = clock.nextDelta();
        resizeScale = FrameClock.approach(resizeScale, targetResizeScale, 18, deltaTime);
        updateAnimations();
        windowPop.update(closing ? 0 : 1, POP_SPEED, closing ? 1 : 0.9f, deltaTime);
        categoryFade = Math.min(1, categoryFade + deltaTime / 0.07f);
        switchFade = Math.min(1, switchFade + deltaTime / 0.1f);

        float x = guiMouseX(mouseX);
        float y = guiMouseY(mouseY);
        if (draggingWindow) {
            windowX = x - dragX;
            windowY = y - dragY;
        } else if (draggedPanel != null) {
            draggedPanel.targetX = x - dragX;
            draggedPanel.targetY = y - dragY;
            clampPanel(draggedPanel);
        }
        layoutWindow();
        positionContent();

        Panel hoveredPanel = panelAt(x, y);
        graphics.nextStratum();
        try (var renderer = new Renderer2D(graphics)) {
            int dim = ClickGuiStyle.dim();
            renderer.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    ARGB.color(Math.round(ARGB.alpha(dim) * dimOpacity()), dim));
        }
        graphics.nextStratum();
        UniformGuiFade.begin(graphics, windowOpacity());

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale());

        try (Renderer2D renderer = new Renderer2D(graphics, TextRenderer.Font.INTER)) {
            float centerX = windowPivotX();
            float centerY = windowPivotY();
            graphics.pose().translate(centerX, centerY);
            graphics.pose().scale(windowZoom());
            graphics.pose().translate(-centerX, -centerY);
            drawWindow(renderer, x, y);

            try (var clip = clip(renderer, windowX + sidebarWidth(), windowY, WINDOW_WIDTH - sidebarWidth(), WINDOW_HEIGHT)) {
                float opacity = FrameClock.smoothStep(categoryFade);
                switchProgress = Math.min(1, switchProgress + deltaTime / SWITCH_DURATION);
                if (switchProgress >= 1) previousCategory = null;
                if (previousCategory == null) {
                    for (Panel panel : panels) {
                        drawCategory(renderer, panel, x, y, opacity);
                    }
                } else {
                    if (switchProgress < 0.5f) {
                        positionPanel(previousCategory);
                        float out = 1 - FrameClock.smoothStep(switchProgress * 2);
                        if (out > 0.002f) drawCategory(renderer, previousCategory, -1, -1, opacity * out);
                    } else {
                        float in = FrameClock.smoothStep(switchProgress * 2 - 1);
                        for (Panel panel : panels) {
                            if (in > 0.002f) drawCategory(renderer, panel, x, y, opacity * in);
                        }
                    }
                }
            }
            drawDescription(renderer, x, y);

        } finally {
            graphics.pose().popMatrix();
        }
        UniformGuiFade.end(graphics);
    }

    private float guiMouseX(double x) {
        float local = (float) x / scale();
        float center = windowPivotX();
        return center + (local - center) / windowZoom();
    }

    private float guiMouseY(double y) {
        float local = (float) y / scale();
        float center = windowPivotY();
        return center + (local - center) / windowZoom();
    }

    private float panelRadius() {
        return PANEL_RADIUS;
    }

    private Category[] sidebarCategories() {
        return configOnly ? new Category[0] : MODULE_CATEGORIES;
    }

    private void layoutWindow() {
        if (Float.isNaN(windowX)) {
            windowX = (screenWidth() - WINDOW_WIDTH) / 2;
            windowY = (screenHeight() - WINDOW_HEIGHT) / 2;
        }
        windowX = Math.clamp(windowX, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenWidth() - WINDOW_WIDTH - SCREEN_MARGIN));
        windowY = Math.clamp(windowY, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight() - WINDOW_HEIGHT - SCREEN_MARGIN));
    }

    private float windowPivotX() {
        return HudPopAnimation.pivotX(windowX, WINDOW_WIDTH, windowEdges());
    }

    private float windowPivotY() {
        return HudPopAnimation.pivotY(windowY, WINDOW_HEIGHT, windowEdges());
    }

    private int windowEdges() {
        return 0;
    }

    private void selectCategory(Category category) {
        if (selectedCategory == category) return;
        selectedCategory = category;
        if (category == Category.CONFIG) configPanel.refresh();
        rememberScroll();
        previousCategory = panels.isEmpty() ? null : panels.getFirst();
        switchProgress = previousCategory == null ? 1 : 0;
        panels.clear();
        Panel panel = new Panel(category, contentX(), windowY + panelTop());
        setScroll(panel, categoryScroll.getOrDefault(category, 0f));
        panels.add(panel);
        draggedSlider = null;
        sliderPanel = null;
        draggedColor = null;
        colorPanel = null;
        draggedPreview = null;
    }

    private void rememberScroll() {
        for (Panel panel : panels) categoryScroll.put(panel.category, panel.targetScroll);
    }

    private static void setScroll(Panel panel, float scroll) {
        panel.scroll = panel.targetScroll = scroll;
        panel.scrollMotion.reset(scroll);
    }

    private static boolean inHudEditor(Module module) {
        return module instanceof HudElement && !(module instanceof DynamicIslandModule);
    }

    public void carryWindow() {
        fadeIn = true;
    }

    public void openConfig() {
        Screen back = returnTo;
        returnTo = null;
        manager.get(ClickGuiModule.class).configScreen().show(back, true);
    }

    public void openHudEditor() {
        Screen back = returnTo;
        returnTo = null;
        minecraft.gui.setScreen(new HudEditorScreen(manager, accent, back, true));
    }

    private void positionContent() {
        if (panels.isEmpty()) return;
        positionPanel(panels.getFirst());
    }

    private void positionPanel(Panel panel) {
        panel.x = panel.targetX = contentX();
        panel.y = panel.targetY = windowY + panelTop();
        panel.xMotion.reset(panel.x);
        panel.yMotion.reset(panel.y);
    }

    private float settingInset() {
        return SETTING_INSET;
    }

    private float sliderInset() {
        return SLIDER_INSET;
    }

    private boolean standalone() {
        return hudOnly || configOnly;
    }

    private float contentX() {
        return windowX + sidebarWidth() + CONTENT_X;
    }

    private float panelTop() {
        return 0;
    }

    private float panelBodyHeight() {
        return WINDOW_HEIGHT - panelTop();
    }

    protected boolean windowContains(double mouseX, double mouseY) {
        return inside(guiMouseX(mouseX), guiMouseY(mouseY), windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    private void drawDescription(Renderer2D renderer, float mouseX, float mouseY) {
        Module module = describing ? describedModule : null;
        describedModule = null;
        if (module != null && module.description() != null && !module.description().isEmpty()) shownDescription = module;
        float reveal = Math.clamp(descriptionReveal.update(module != null ? 1 : 0, 26, 1, deltaTime), 0, 1);
        if (reveal <= 0.002f || shownDescription == null) return;
        String text = shownDescription.description();
        var size = renderer.text.measureWrapped(text, DESCRIPTION_FONT, DESCRIPTION_WIDTH - DESCRIPTION_PAD * 2);
        float width = size.width() + DESCRIPTION_PAD * 2;
        float height = size.height() + DESCRIPTION_PAD * 2;
        float x = Math.min(mouseX + 10, windowX + WINDOW_WIDTH - width - 4);
        float y = mouseY + 12;
        if (y + height > windowY + WINDOW_HEIGHT - 4) y = mouseY - height - 6;
        float baseOpacity = renderer.opacity();
        renderer.opacity(baseOpacity * reveal);
        try {
            renderer.sdf.roundedRect(x, y + 4 * (1 - reveal), width, height, ClickGuiStyle.CARD_RADIUS - 2, ClickGuiStyle.raised());
            renderer.text.drawWrapped(text, x + DESCRIPTION_PAD, y + 4 * (1 - reveal) + DESCRIPTION_PAD, DESCRIPTION_FONT,
                    DESCRIPTION_WIDTH - DESCRIPTION_PAD * 2, ClickGuiStyle.text());
        } finally {
            renderer.opacity(baseOpacity);
        }
    }

    private void drawCategory(Renderer2D renderer, Panel panel, float mouseX, float mouseY, float opacity) {
        try {
            renderer.opacity(opacity);
            if (panel.category == Category.CONFIG) {
                configPanel.render(renderer, panel.x, panel.y + CONTENT_PAD_Y, contentWidth(),
                        panelBodyHeight() - CONTENT_PAD_Y * 2, mouseX, mouseY, accent.getAsInt());
            } else {
                drawPanel(renderer, panel, mouseX, mouseY);
            }
        } finally {
            renderer.opacity(1);
        }
    }

    private void drawWindow(Renderer2D renderer, float mouseX, float mouseY) {
        float radius = ClickGuiStyle.WINDOW_RADIUS;
        if (manager.get(ClickGuiModule.class).shadows()) {
            HudBackdrop.draw(renderer, List.of(HudBackdrop.docked(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, radius,
                    windowEdges())));
        }
        renderer.sdf.roundedRect(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, radius, ClickGuiStyle.header());
        float reach = radius + 1;
        renderer.sdf.clippedDockedRect(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, radius, ClickGuiStyle.sidebar(),
                windowEdges(), windowX - reach, windowY - reach, windowX + sidebarWidth(), windowY + WINDOW_HEIGHT + reach);
        renderer.sdf.clippedDockedRect(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, radius, ClickGuiStyle.header(),
                windowEdges(), windowX + sidebarWidth(), windowY - reach, windowX + WINDOW_WIDTH + reach, windowY + WINDOW_HEIGHT + reach);
        renderer.sdf.roundedRect(windowX + sidebarWidth() - 1, windowY, 1, WINDOW_HEIGHT, 0,
                mix(ClickGuiStyle.sidebar(), ClickGuiStyle.text(), 0.05f));
        float reveal = FrameClock.smoothStep(switchFade);
        float tabsOpacity = configOnly ? 1 : standalone() ? 1 - reveal : reveal;
        float baseOpacity = renderer.opacity();
        float brandOpacity = standalone() ? 1 - reveal : reveal;
        if (brandOpacity > 0.001f) {
            renderer.opacity(baseOpacity * brandOpacity);
            try {
                drawBrand(renderer);
            } finally {
                renderer.opacity(baseOpacity);
            }
        }
        renderer.opacity(baseOpacity * tabsOpacity);
        try {
            drawTabs(renderer, mouseX, mouseY, tabsOpacity > 0.001f);
        } finally {
            renderer.opacity(baseOpacity);
        }
    }

    private float[] brandLayout(Renderer2D renderer) {
        TextRenderer brandText = renderer.text.withFont(TextRenderer.Font.INTER_MEDIUM);
        float fit = Math.min(1, (sidebarWidth() - SIDEBAR_EDGE * 2) / brandWidth(renderer.text));
        float height = BRAND_LOGO_HEIGHT * fit;
        float width = 0;
        float fontSize = BRAND_FONT_SIZE * fit;
        float versionSize = BRAND_VERSION_SIZE * fit;
        float gap = 0;
        float textWidth = brandText.measure(BRAND_TEXT, fontSize).width();
        float centerY = windowY + TITLE_HEIGHT / 2;
        float versionWidth = renderer.text.measure(BRAND_VERSION, versionSize).width();
        float total = width + gap + textWidth + 2 * fit + versionWidth;
        float left = windowX + (sidebarWidth() - total) / 2;
        float top = centerY - height / 2;
        float baseline = centerY + fontSize * BRAND_CAP_HEIGHT / 2;
        float textX = left + width + gap;
        float versionTop = baseline - fontSize * BRAND_CAP_HEIGHT
                - renderer.text.ascent(versionSize) + versionSize * BRAND_CAP_HEIGHT;
        return new float[] {left, top, width, height, 0,
                textX, baseline - brandText.ascent(fontSize), fontSize,
                textX + textWidth + 2 * fit, versionTop, versionSize, 1};
    }

    private float brandWidth(TextRenderer text) {
        return text.withFont(TextRenderer.Font.INTER_MEDIUM).measure(BRAND_TEXT, BRAND_FONT_SIZE).width() + 2
                + text.measure(BRAND_VERSION, BRAND_VERSION_SIZE).width();
    }

    private float categoryPad() {
        return (ROW_HEIGHT - CARD_INSET * 2 - SIDEBAR_ICON) / 2;
    }

    private float sidebarWidth() {
        if (sidebarWidth > 0) return sidebarWidth;
        float widest = 0;
        for (Category category : MODULE_CATEGORIES) {
            widest = Math.max(widest, measureText.measure(category.title(), MODULE_FONT_SIZE).width());
        }
        sidebarWidth = Math.round((SIDEBAR_EDGE + categoryPad()) * 2 + SIDEBAR_ICON + SIDEBAR_GAP + widest + SIDEBAR_EXTRA);
        return sidebarWidth;
    }

    private void drawBrand(Renderer2D renderer) {
        float[] layout = brandLayout(renderer);
        TextRenderer brandText = renderer.text.withFont(TextRenderer.Font.INTER_MEDIUM);
        String first = BRAND_TEXT.substring(0, 1);
        String rest = BRAND_TEXT.substring(1);
        float restX = layout[5] + brandText.measure(BRAND_TEXT, layout[7]).width() - brandText.measure(rest, layout[7]).width();
        brandText.drawColored(first, layout[5], layout[6], layout[7], AccentWave.pulse());
        brandText.draw(rest, restX, layout[6], layout[7], ClickGuiStyle.text());
        renderer.text.draw(BRAND_VERSION, layout[8], layout[9], layout[10], ClickGuiStyle.muted());
    }

    private void layoutTabs() {
        Category[] categories = sidebarCategories();
        float inset = SIDEBAR_EDGE;
        float x = windowX + inset;
        float width = sidebarWidth() - inset * 2;
        for (int i = 0; i < categories.length; i++) {
            float y = windowY + CONTENT_PAD_Y + CARD_INSET + TAB_DROP + (i + 1) * ROW_HEIGHT;
            tabRects[i] = new float[] {x, y, width, ROW_HEIGHT - CARD_INSET * 2, width};
        }
    }

    private void drawTabs(Renderer2D renderer, float mouseX, float mouseY, boolean interactive) {
        layoutTabs();
        Category[] categories = sidebarCategories();
        int selectedIndex = -1;
        for (int i = 0; i < categories.length; i++) if (categories[i] == selectedCategory) selectedIndex = i;
        float radius = ClickGuiStyle.CARD_RADIUS;
        TextRenderer nameText = renderer.text;
        float pad = categoryPad();
        if (selectedIndex >= 0) {
            float target = tabRects[selectedIndex][1] - windowY;
            if (highlightY.value() < 0) highlightY.reset(target);
            float y = windowY + highlightY.update(target, 24, 0.85f, deltaTime);
            float[] rect = tabRects[selectedIndex];
            float targetWidth = tabFit(nameText, categories[selectedIndex], pad, rect[2]);
            if (highlightWidth.value() < 0) highlightWidth.reset(targetWidth);
            float width = highlightWidth.update(targetWidth, 24, 0.85f, deltaTime);
            float phase = ThemeColors.wavePhase();
            int slices = Math.max(1, (int) Math.ceil(width / 3));
            for (int slice = 0; slice < slices; slice++) {
                float from = rect[0] + width * slice / slices;
                float to = rect[0] + width * (slice + 1) / slices;
                int wave = ThemeColors.wave(ThemeColors.primary(), ThemeColors.secondary(), (from + to) / 2, phase);
                renderer.sdf.clippedRoundedRect(rect[0], y, width, rect[3], radius,
                        mix(ClickGuiStyle.sidebar(), ARGB.opaque(wave), 0.45f),
                        slice == 0 ? from - 1 : from, y - 1, slice == slices - 1 ? to + 1 : to, y + rect[3] + 1);
            }
        }
        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            float[] rect = tabRects[i];
            boolean selected = category == selectedCategory;
            boolean hovered = interactive && inside(mouseX, mouseY, rect[0], rect[1], rect[4], rect[3]);
            float hover = Math.clamp(tabHover.computeIfAbsent(category, ignored -> new SpringAnimation())
                    .update(hovered && !selected ? 1 : 0, 24, deltaTime), 0, 1);
            float selection = Math.clamp(categorySelection.computeIfAbsent(category, ignored -> new SpringAnimation(
                    selected ? 1 : 0)).update(selected ? 1 : 0, 26, 1, deltaTime), 0, 1);
            if (hover > 0.001f) {
                int fill = ClickGuiStyle.hover();
                float width = Math.min(rect[2], tabFit(nameText, category, pad, rect[2]) + hover * 2);
                renderer.sdf.roundedRect(rect[0], rect[1], width, rect[3], radius,
                        ARGB.color(Math.round(ARGB.alpha(fill) * hover * 0.6f), fill));
            }
            float centerY = rect[1] + rect[3] / 2;
            float iconX = rect[0] + pad + hover * 2;
            int color = mix(mix(ClickGuiStyle.muted(), 0xFFFFFFFF, hover), 0xFFFFFFFF, selection);
            renderer.textures.icon(category.icon(), iconX, centerY - SIDEBAR_ICON / 2, SIDEBAR_ICON, color);
            nameText.draw(category.title(), iconX + SIDEBAR_ICON + SIDEBAR_GAP,
                    centerY - nameText.lineHeight(MODULE_FONT_SIZE) / 2, MODULE_FONT_SIZE, color);
        }
    }

    private static float tabFit(TextRenderer text, Category category, float pad, float maximum) {
        return Math.min(maximum, pad * 2 + SIDEBAR_ICON + SIDEBAR_GAP + text.measure(category.title(), MODULE_FONT_SIZE).width());
    }

    private Category tabAt(float x, float y) {
        Category[] categories = sidebarCategories();
        for (int i = 0; i < categories.length; i++) {
            float[] rect = tabRects[i];
            if (rect != null && inside(x, y, rect[0], rect[1] - CARD_INSET, rect[4], rect[3] + CARD_INSET * 2)) return categories[i];
        }
        return null;
    }

    private void drawPanel(Renderer2D renderer, Panel panel, float mouseX, float mouseY) {
        List<Row> rows = rows(panel);
        float body = visibleBodyHeight(panel, rows);
        float panelHeight = body;
        if (panel.amount == 0) {
            return;
        }

        float height = body;
        float top = panel.y;
        if (rows.isEmpty()) {
            renderer.sdf.clippedRoundedRect(panel.x, panel.y, panelWidth(), panelHeight, panelRadius(), ClickGuiStyle.header(),
                    panel.x - 1, top, panel.x + panelWidth() + 1, top + height + 1);
            try (var clip = clip(renderer, panel.x, top, panelWidth(), height)) {
                label(renderer, "Empty", panel.x + CONTENT_INSET,
                        top + ROW_HEIGHT / 2, ClickGuiStyle.muted());
            }
        }
        drawModuleCards(renderer, panel, rows, top, height, mouseX, mouseY);
    }

    private float settingCornerGap() {
        return SETTING_CARD_INSET - CARD_INSET;
    }

    private float settingsRadius() {
        return PANEL_RADIUS - 7;
    }

    private record Block(int start, int end, float x, float y, float height) {
    }

    private record Placed(Row row, float x, float y) {
    }

    private List<Block> blocks(float originX, List<Row> rows) {
        List<Block> blocks = new ArrayList<>();
        float[] column = {CONTENT_PAD_Y, CONTENT_PAD_Y};
        int side = 0;
        int index = 0;
        while (index < rows.size()) {
            Row first = rows.get(index);
            int end = index;
            float height = 0;
            while (end < rows.size() && rows.get(end).module() == first.module()) {
                height += rows.get(end).height();
                end++;
            }
            blocks.add(new Block(index, end, originX + side * (columnWidth() + COLUMN_GAP), column[side], height));
            column[side] += height;
            index = end;
        }
        return blocks;
    }

    private List<Placed> placedRows(Panel panel, List<Row> rows) {
        List<Placed> placed = new ArrayList<>();
        float top = panel.y - panel.scroll;
        for (Block block : blocks(panel.x, rows)) {
            float y = top + block.y();
            for (int i = block.start(); i < block.end(); i++) {
                Row row = rows.get(i);
                placed.add(new Placed(row, block.x(), y));
                y += row.height();
            }
        }
        return placed;
    }

    private void drawModuleCards(Renderer2D renderer, Panel panel, List<Row> rows, float panelTop, float panelHeight,
                                 float mouseX, float mouseY) {
        float top = panelTop + VIEW_INSET;
        float height = Math.max(0, panelHeight - VIEW_INSET * 2);
        try (var clip = clip(renderer, panel.x, top, panelSpan(), height)) {
            for (Block block : blocks(panel.x, rows)) {
                float y = panelTop - panel.scroll + block.y();
                if (y + block.height() <= top || y >= top + height) continue;
                Module module = rows.get(block.start()).module();
                ModuleAnimation animation = moduleAnimation(module);
                float presence = Math.clamp(animation.enabled, 0, 1);
                int background = mix(mix(ClickGuiStyle.card(), ClickGuiStyle.cardEnabled(), presence * 0.6f), ClickGuiStyle.hover(),
                        animation.hover * 0.5f);
                renderer.sdf.roundedRect(block.x() + CARD_INSET, y + CARD_INSET, panelWidth() - CARD_INSET * 2,
                        Math.max(0, block.height() - CARD_INSET * 2), ClickGuiStyle.CARD_RADIUS, background);
                float groupY = y;
                for (int i = block.start(); i < block.end(); i++) {
                    Row row = rows.get(i);
                    if (row.setting() != null && !row.spacer() && row.groupOffset() == 0) {
                        renderer.sdf.roundedRect(block.x() + SETTING_CARD_INSET, groupY,
                                panelWidth() - SETTING_CARD_INSET * 2, row.groupHeight(),
                                settingsRadius(), ClickGuiStyle.settings());
                    }
                    groupY += row.height();
                }
                float rowY = y;
                for (int i = block.start(); i < block.end(); i++) {
                    Row row = rows.get(i);
                    if (rowY + row.height() > top && rowY < top + height) {
                        boolean hovered = inside(mouseX, mouseY, block.x(), rowY, panelWidth(), row.height())
                                && inside(mouseX, mouseY, panel.x, top, panelSpan(), height);
                        drawRow(renderer, row, block.x(), rowY, hovered, top, top + height, panel);
                    }
                    rowY += row.height();
                }
            }
        }
    }

    private void drawRow(Renderer2D renderer, Row row, float x, float y, boolean hovered, float viewportTop, float viewportBottom, Panel panel) {
        float top = Math.max(y, viewportTop);
        float bottom = Math.min(y + row.height(), viewportBottom);
        if (row.spacer()) {
            return;
        }
        Setting<?> setting = row.setting();
        ModuleAnimation moduleAnimation = moduleAnimation(row.module());
        SettingAnimation settingAnimation = setting == null ? null : settingAnimation(setting);
        float hover;
        if (setting == null) {
            moduleAnimation.hover = moduleAnimation.hoverMotion.update(hovered ? 1 : 0, 20, 0.95f, deltaTime);
            hover = moduleAnimation.hover;
        } else if (row.modeChoice() != null) {
            hover = animate(settingAnimation.modeHover.computeIfAbsent(row.modeChoice(), ignored -> new SpringAnimation()),
                    hovered ? 1 : 0, 18);
        } else {
            settingAnimation.hover = animate(settingAnimation.hoverMotion, hovered ? 1 : 0, 18);
            hover = settingAnimation.hover;
        }
        if (setting == null) {
        } else {
            drawSettingBackground(renderer, row, panel, x, y, viewportTop, viewportBottom, hover);
        }
        y += row.pad();
        settingFont = row.pad() > 0 ? PAGE_FONT_SIZE : ClickGuiStyle.SETTING_FONT_SIZE;

        try (var rowClip = clip(renderer, x, top, panelWidth(), bottom - top)) {
            if (setting == null) {
                if (hovered) describedModule = row.module();
                drawModuleHeader(renderer, row, moduleAnimation, x, y);
                return;
            }

            if (row.colorPicker()) {
                drawColorPicker(renderer, (ColorSetting) setting, settingAnimation, x, y);
                return;
            }

            if (setting instanceof PlayerPreviewSetting preview) {
                playerPreviews.computeIfAbsent(preview, ignored -> new EspPlayerPreview())
                        .draw(renderer, preview.module(), x + 12, y,
                                panelWidth() - 24);
                return;
            }

            if (row.modeChoice() != null) {
                boolean selected = ((ChoiceSetting) setting).selectedChoice().equals(row.modeChoice());
                String option = row.modeChoice();
                label(renderer, option, x + settingInset(), y + SETTING_ROW_HEIGHT / 2,
                        settingFont, AccentWave.wave(ClickGuiStyle.text(), selected ? 1 : 0));
                return;
            }

            if (setting instanceof ActionSetting) {
                label(renderer, setting.name(), x + settingInset(), y + SETTING_ROW_HEIGHT / 2,
                        settingFont, AccentWave.text(ClickGuiStyle.text(), hover));
                label(renderer, "›", x + panelWidth() - settingInset() - 5, y + SETTING_ROW_HEIGHT / 2,
                        FONT_SIZE, AccentWave.text());
                return;
            }

            drawSetting(renderer, setting, settingAnimation, x, y);
        }
    }

    private void drawModuleName(TextRenderer text, String name, float x, float y, TextRenderer.ColorFunction colors) {
        text.drawColored(name, x, y, MODULE_FONT_SIZE, colors);
    }

    private void drawModuleHeader(Renderer2D renderer, Row row, ModuleAnimation moduleAnimation, float x, float y) {
        y += (row.height() - ROW_HEIGHT) / 2;
        float pad = (ROW_HEIGHT - CARD_INSET * 2 - MODULE_FONT_SIZE * BRAND_CAP_HEIGHT) / 2;
        float nameX = x + CARD_INSET + pad;
        float switchX = x + panelWidth() - CARD_INSET - pad - 28.5f;
        float right = switchX - 12;
        String key = row.module().bind().displayValue();
        if (row.module().bind().get() != -1) {
            key = fitLabel(renderer, key.toUpperCase(java.util.Locale.ROOT), 9, 78);
            float keyWidth = renderer.text.measure(key, 9).width();
            float boxX = right - keyWidth - 16;
            float boxY = y + (ROW_HEIGHT - 14) / 2;
            renderer.text.drawColored(key, boxX + 6, renderer.text.topInBox(boxY, 14, 9), 9, AccentWave.text());
            right -= keyWidth + 24;
        }
        TextRenderer moduleText = renderer.text;
        drawModuleName(moduleText, fitLabel(moduleText, row.module().name(), MODULE_FONT_SIZE, right - nameX - 8),
                nameX, y + ROW_HEIGHT / 2 - moduleText.lineHeight(MODULE_FONT_SIZE) / 2,
                AccentWave.wave(ClickGuiStyle.text(), Math.clamp(moduleAnimation.enabled, 0, 1)));

        drawToggleControl(renderer, switchX, y + ROW_HEIGHT / 2, moduleAnimation.enabled);
    }

    private void drawSettingBackground(Renderer2D renderer, Row row, Panel panel, float x, float y,
                                       float viewportTop, float viewportBottom, float hover) {
        float top = Math.max(y, viewportTop);
        float bottom = Math.min(y + row.height(), viewportBottom);
        if (hover <= 0.001f) return;
        int background = mix(ClickGuiStyle.settings(), ClickGuiStyle.hover(), hover);
        float inset = SETTING_CARD_INSET;
        float radius = settingsRadius();
        float groupTop = y - row.groupOffset();
        renderer.sdf.clippedRoundedRect(x + inset, groupTop, panelWidth() - inset * 2,
                row.groupHeight(), radius, background,
                x + inset - 1, top, x + panelWidth() - inset + 1, bottom);
    }

    private String getSettingValue(Setting<?> setting) {
        if (setting == binding) {
            return "Press input...";
        }
        if (setting instanceof BooleanSetting || setting instanceof ColorSetting) {
            return "";
        }
        if (setting instanceof IntSetting slider) {
            return slider.displayValue(settingAnimation(setting).fraction);
        }
        return setting.displayValue();
    }

    private void drawSetting(Renderer2D renderer, Setting<?> setting, SettingAnimation settingAnimation,
                             float x, float y) {
        String value = fitLabel(renderer, getSettingValue(setting), settingFont,
                (panelWidth() - settingInset() * 2) * 0.48f);
        float valueWidth = renderer.text.measure(value, settingFont).width();
        float dropdownInset = setting instanceof ChoiceSetting ? 12 : 0;
        boolean editing = setting == editingSlider;
        float typedWidth = editing ? renderer.text.measure(sliderInput.getValue() + editingSlider.typedSuffix(),
                settingFont).width() : 0;
        float reserved = setting instanceof BooleanSetting ? 36
                : editing ? typedWidth + 4 : valueWidth + 4 + dropdownInset;
        if (setting instanceof ColorSetting) {
            reserved += 19;
        }
        float nameWidth = Math.max(0, panelWidth() - settingInset() * 2 - reserved);
        label(renderer, fitLabel(renderer, setting.name(), settingFont, nameWidth),
                x + settingInset(), y + SETTING_ROW_HEIGHT / 2 + SETTING_TEXT_OFFSET,
                settingFont, ClickGuiStyle.text());
        if (setting instanceof ColorSetting color) {
            renderer.sdf.roundedRect(x + panelWidth() - settingInset() - 14, y + SETTING_ROW_HEIGHT / 2 - 6, 14, 12, 3,
                    color.get());
        } else if (setting instanceof BooleanSetting) {
            drawToggle(renderer, x, y, settingAnimation.enabled);
        } else if (editing) {
            drawSliderInput(renderer, x + panelWidth() - settingInset(), y + SETTING_ROW_HEIGHT / 2 + SETTING_TEXT_OFFSET);
        } else {
            settingAnimation.valueLeft = x + panelWidth() - settingInset() - valueWidth - dropdownInset;
            label(renderer, value, settingAnimation.valueLeft,
                    y + SETTING_ROW_HEIGHT / 2 + SETTING_TEXT_OFFSET,
                    settingFont, AccentWave.text(ClickGuiStyle.text(),
                            setting instanceof BindSetting || setting instanceof ChoiceSetting ? 0 : settingAnimation.pulse));
        }
        if (setting instanceof ChoiceSetting) {
            drawExpandIcon(renderer, x + panelWidth() - settingInset() - 3,
                    y + SETTING_ROW_HEIGHT / 2, expandedModes.contains(setting), ClickGuiStyle.text());
        }
        if (setting instanceof SliderSetting) {
            drawSlider(renderer, x, y, settingAnimation);
        }
    }

    private void drawSliderInput(Renderer2D renderer, float right, float centerY) {
        String typed = sliderInput.getValue();
        String suffix = editingSlider.typedSuffix();
        float numberRight = right - renderer.text.measure(suffix, settingFont).width();
        float left = numberRight - renderer.text.measure(typed, settingFont).width();
        sliderInputLeft = left;
        sliderInputRight = right;
        sliderInputCenterY = centerY;
        if (!Float.isNaN(sliderClickX)) {
            int closest = 0;
            float nearest = Float.MAX_VALUE;
            for (int index = 0; index <= typed.length(); index++) {
                float stop = left + renderer.text.measure(typed.substring(0, index), settingFont).width();
                if (Math.abs(stop - sliderClickX) < nearest) {
                    nearest = Math.abs(stop - sliderClickX);
                    closest = index;
                }
            }
            sliderInput.setCursorPosition(closest);
            sliderInput.setHighlightPos(closest);
            sliderClickX = Float.NaN;
        }
        int cursor = sliderInput.getCursorPosition();
        float caret = left + renderer.text.measure(typed.substring(0, cursor), settingFont).width();
        String selected = sliderInput.getHighlighted();
        if (!selected.isEmpty()) {
            int end = cursor + selected.length();
            if (end > typed.length() || !typed.substring(cursor, end).equals(selected)) end = cursor - selected.length();
            float other = left + renderer.text.measure(typed.substring(0, end), settingFont).width();
            renderer.sdf.roundedRect(Math.min(caret, other), centerY - 8, Math.abs(other - caret), 16, 3,
                    ARGB.color(0x50, accent.getAsInt()));
        }
        label(renderer, typed, left, centerY, settingFont, ClickGuiStyle.text());
        label(renderer, suffix, numberRight, centerY, settingFont, ClickGuiStyle.text());
        float idle = (System.nanoTime() - sliderEditedAt) * 1e-9f;
        if (idle < 0.5f || idle % 1 < 0.5f) {
            renderer.sdf.roundedRect(caret, centerY - 7, 1, 14, 0.5f, accent.getAsInt());
        }
    }

    private void drawToggle(Renderer2D renderer, float x, float y, float amount) {
        float switchX = x + panelWidth() - settingInset() - 28;
        float switchY = y + SETTING_ROW_HEIGHT / 2 + SETTING_TEXT_OFFSET;
        drawToggleControl(renderer, switchX, switchY, amount);
    }

    private void drawToggleControl(Renderer2D renderer, float switchX, float switchY, float amount) {
        float position = Math.clamp(amount, -0.12f, 1.12f);
        amount = Math.clamp(amount, 0, 1);
        float left = switchX + 4;
        float knobX = switchX + 9.5f + 13 * position;
        renderer.sdf.roundedRect(left, switchY - 3.5f, 24, 7, 3.5f, ClickGuiStyle.track());
        float fill = Math.clamp(knobX - left, 0, 24);
        if (fill > 0) renderer.sdf.roundedRect(left, switchY - 3.5f, fill, 7, 3.5f, accent.getAsInt());
        renderer.sdf.circle(knobX, switchY, 6, mix(ClickGuiStyle.muted(), ClickGuiStyle.text(), amount));
    }

    private void drawSlider(Renderer2D renderer, float x, float y, SettingAnimation animation) {
        float left = x + sliderInset();
        float width = panelWidth() - sliderInset() * 2;
        float centerY = y + SLIDER_TRACK_Y;
        float progress = width * Math.clamp(animation.fraction, 0, 1);
        float interaction = Math.clamp(animation.sliderInteraction, 0, 1.1f);
        float trackHeight = SLIDER_TRACK_HEIGHT + interaction;
        float radius = trackHeight / 2;
        renderer.sdf.roundedRect(left, centerY - radius, width, trackHeight, radius,
                mix(ClickGuiStyle.track(), ClickGuiStyle.disabled(), interaction * 0.3f));
        if (progress > 0) {
            renderer.roundedGradient(left, centerY - radius, progress, trackHeight, radius,
                    accent.getAsInt(), mix(ThemeColors.secondary(), ClickGuiStyle.text(), interaction * 0.18f));
        }
        float thumbRadius = SLIDER_KNOB_RADIUS + interaction * 1.35f;
        renderer.sdf.circle(left + progress, centerY, thumbRadius, ClickGuiStyle.text());
    }

    private void drawColorPicker(Renderer2D renderer, ColorSetting color, SettingAnimation animation, float x, float y) {
        float left = x + settingInset();
        float top = y + 4;
        int hue = ColorSetting.rgb(color.hue(), 1, 1);
        renderer.colorField(left, top, colorFieldWidth(), COLOR_FIELD_HEIGHT, PICKER_RADIUS, hue);
        float hueTop = top + COLOR_FIELD_HEIGHT + HUE_GAP;
        renderer.hueBar(left, hueTop, colorFieldWidth(), HUE_HEIGHT);
        float cursorX = Math.clamp(left + animation.saturation * colorFieldWidth(), left + 5, left + colorFieldWidth() - 5);
        float cursorY = Math.clamp(top + (1 - animation.brightness) * COLOR_FIELD_HEIGHT, top + 5, top + COLOR_FIELD_HEIGHT - 5);
        renderer.sdf.circle(cursorX, cursorY, 4, ClickGuiStyle.header());
        renderer.sdf.circle(cursorX, cursorY, 3, ClickGuiStyle.text());
        float hueX = left + animation.hue * colorFieldWidth();
        renderer.sdf.circle(hueX, hueTop + HUE_HEIGHT / 2, 5, ClickGuiStyle.header());
        renderer.sdf.circle(hueX, hueTop + HUE_HEIGHT / 2, 4, ClickGuiStyle.text());
    }

    private void updateColor(float x, float y) {
        for (Placed placed : placedRows(colorPanel, rows(colorPanel))) {
            Row row = placed.row();
            float rowY = placed.y();
            if (row.setting() == draggedColor && row.colorPicker()) {
                colorX = placed.x();
                float vertical = Math.clamp((y - rowY - 4) / COLOR_FIELD_HEIGHT, 0, 1);
                if (draggingHue) {
                    float hue = Math.clamp((x - colorX - settingInset()) / colorFieldWidth(), 0, 1);
                    draggedColor.select(hue, draggedColor.saturation(), draggedColor.brightness());
                } else {
                    float saturation = Math.clamp((x - colorX - settingInset()) / colorFieldWidth(), 0, 1);
                    draggedColor.select(draggedColor.hue(), saturation, 1 - vertical);
                }
                return;
            }
        }
    }

    private List<Row> rows(Panel panel) {
        List<Row> rows = new ArrayList<>();
        for (Module module : manager.modules()) {
            if (module instanceof DynamicIslandModule
                    || (hudOnly ? !inHudEditor(module)
                    : inHudEditor(module) || module.category() != panel.category)) {
                continue;
            }
            rows.add(new Row(module, null, 1));
            float amount = moduleAnimation(module).open;
            if (amount > 0) {
                List<Row> settingsRows = settingsRows(module);
                float settingsHeight = 0;
                for (Row row : settingsRows) {
                    settingsHeight += row.height();
                }
                float bottomPadding = settingCornerGap() + CARD_INSET;
                float padding = SETTINGS_SPACING + bottomPadding;
                float visibleHeight = (settingsHeight + padding) * amount;
                float spacingAmount = Math.min(1, visibleHeight / padding);
                rows.add(Row.spacing(module, spacingAmount));
                float remaining = Math.max(0, visibleHeight - padding);
                for (Row row : settingsRows) {
                    float rowHeight = row.height();
                    float reveal = Math.clamp(remaining / rowHeight, 0, 1);
                    if (reveal > 0) {
                        float groupHeight = Math.min(row.groupHeight(), remaining + row.groupOffset());
                        rows.add(new Row(module, row.setting(), row.amount() * reveal, row.groupOffset(),
                                groupHeight, row.spacer(), row.modeChoice(), row.colorPicker()));
                    }
                    remaining -= rowHeight;
                }
                rows.add(Row.spacing(module, (bottomPadding * spacingAmount + Math.max(0, remaining)) / SETTINGS_SPACING));
            }
        }
        return rows;
    }

    private List<Row> settingsRows(Module module) {
        List<Row> rows = new ArrayList<>();
        List<Row> group = new ArrayList<>();
        for (Setting<?> setting : module.settings()) {
            float visibility = settingAnimation(setting).visibility;
            if (visibility == 0) {
                continue;
            }
            if (setting instanceof ColorSetting color) {
                appendSettingsGroup(rows, group, module);
                group.clear();
                group.add(new Row(module, setting, visibility));
                float reveal = settingAnimation(color).dropdown * visibility;
                if (reveal > 0) {
                    group.add(new Row(module, setting, reveal, 0, 0, false, null, true));
                }
                appendSettingsGroup(rows, group, module);
                group.clear();
            } else if (setting instanceof ChoiceSetting mode) {
                appendSettingsGroup(rows, group, module);
                group.clear();
                group.add(new Row(module, setting, visibility));
                List<String> choices = new ArrayList<>(mode.choices());
                choices.remove(mode.selectedChoice());
                float remaining = choices.size() * SETTING_ROW_HEIGHT * settingAnimation(setting).dropdown * visibility;
                for (String choice : choices) {
                    float reveal = Math.clamp(remaining / SETTING_ROW_HEIGHT, 0, 1);
                    if (reveal > 0) {
                        group.add(new Row(module, setting, reveal, 0, 0, false, choice, false));
                    }
                    remaining -= SETTING_ROW_HEIGHT;
                }
                appendSettingsGroup(rows, group, module);
                group.clear();
            } else {
                group.add(new Row(module, setting, visibility));
            }
        }
        appendSettingsGroup(rows, group, module);
        return rows;
    }

    private void appendSettingsGroup(List<Row> rows, List<Row> group, Module module) {
        if (group.isEmpty()) {
            return;
        }
        if (!rows.isEmpty()) {
            float visibility = 0;
            for (Row row : group) {
                visibility = Math.max(visibility, row.amount());
            }
            rows.add(Row.spacing(module, visibility));
        }
        float height = 0;
        for (Row row : group) {
            height += row.height();
        }
        float offset = 0;
        for (Row row : group) {
            rows.add(new Row(module, row.setting(), row.amount(), offset, height, false, row.modeChoice(), row.colorPicker()));
            offset += row.height();
        }
    }

    private float contentHeight(List<Row> rows) {
        float height = 0;
        for (Block block : blocks(0, rows)) {
            height = Math.max(height, block.y() + block.height() + CONTENT_PAD_Y);
        }

        return Math.max(ROW_HEIGHT, height);
    }

    private float bodyHeight(Panel panel, List<Row> rows) {
        return panelBodyHeight();
    }

    private float visibleBodyHeight(Panel panel, List<Row> rows) {
        return bodyHeight(panel, rows) * panel.amount;
    }

    private float maxScroll(Panel panel, List<Row> rows) {
        return Math.max(0, contentHeight(rows) - Math.max(0, bodyHeight(panel, rows)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) return true;
        if (editingSlider != null) {
            float clickX = guiMouseX(event.x());
            if (inside(clickX, guiMouseY(event.y()), sliderInputLeft - 4, sliderInputCenterY - 10,
                    sliderInputRight - sliderInputLeft + 8, 20)) {
                sliderClickX = clickX;
                sliderEditedAt = System.nanoTime();
                return true;
            }
            stopEditingSlider(true);
        }
        float x = guiMouseX(event.x());
        float y = guiMouseY(event.y());
        if (binding != null) {
            binding.bindMouse(event.button());
            binding = null;
            return true;
        }
        int button = event.button();
        if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
            describing = true;
            return true;
        }
        if (!hudOnly && button == InputConstants.MOUSE_BUTTON_LEFT) {
            configPanel.unfocus();
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT
                && inside(x, y, windowX, windowY, sidebarWidth(), WINDOW_HEIGHT)) {
            Category tab = standalone() ? null : tabAt(x, y);
            if (tab != null) {
                selectCategory(tab);
                return true;
            }
            draggingWindow = true;
            dragX = x - windowX;
            dragY = y - windowY;
            return true;
        }
        if (selectedCategory == Category.CONFIG) return configPanel.mouseClicked(x, y, button);
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            List<Row> rows = rows(panel);
            float height = visibleBodyHeight(panel, rows);
            if (!inside(x, y, panel.x, panel.y + VIEW_INSET, panelSpan(), height - VIEW_INSET * 2)) {
                continue;
            }
            panels.remove(i);
            panels.add(panel);
            if (y < panel.y) {
                if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                    panel.open = !panel.open;
                } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                    draggedPanel = panel;
                    dragX = x - panel.x;
                    dragY = y - panel.y;
                }
                return true;
            }
            if (!panel.open || y < panel.y
                    || y >= panel.y + height) {
                return true;
            }
            for (Placed placed : placedRows(panel, rows)) {
                Row row = placed.row();
                float rowX = placed.x();
                float rowY = placed.y();
                if (x >= rowX && x < rowX + panelWidth() && y >= rowY && y < rowY + row.height()) {
                    if (row.spacer()) {
                        return true;
                    }
                    if (row.setting() == null) {
                        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                            row.module().toggle();
                        } else if (button == InputConstants.MOUSE_BUTTON_RIGHT && !row.module().settings().isEmpty()) {
                            if (!expanded.remove(row.module())) expanded.add(row.module());
                        }
                    } else if (button == InputConstants.MOUSE_BUTTON_LEFT && expanded.contains(row.module())
                            && row.setting().isVisible() && settingAnimation(row.setting()).visibility > 0.95f
                            && moduleAnimation(row.module()).open > 0.95f) {
                        if (row.setting() instanceof PlayerPreviewSetting preview) {
                            draggedPreview = preview;
                        } else if (row.setting() instanceof ColorSetting color) {
                            if (row.colorPicker()) {
                                if (settingAnimation(color).dropdown < 0.95f || !expandedColors.contains(color)) {
                                    return true;
                                }
                                float fieldX = rowX + settingInset();
                                if (inside(x, y, fieldX, rowY + 4, colorFieldWidth(), COLOR_FIELD_HEIGHT)
                                        || inside(x, y, fieldX, rowY + 4 + COLOR_FIELD_HEIGHT + HUE_GAP, colorFieldWidth(), HUE_HEIGHT)) {
                                    draggedColor = color;
                                    colorPanel = panel;
                                    colorX = rowX;
                                    draggingHue = y >= rowY + 4 + COLOR_FIELD_HEIGHT + HUE_GAP;
                                    updateColor(x, y);
                                }
                            } else if (!expandedColors.remove(color)) {
                                expandedColors.add(color);
                            }
                        } else if (row.setting() instanceof ChoiceSetting mode) {
                            if (row.modeChoice() != null) {
                                if (settingAnimation(row.setting()).dropdown < 0.95f || !expandedModes.contains(row.setting())) {
                                    return true;
                                }
                                mode.selectChoice(row.modeChoice());
                                expandedModes.remove(row.setting());
                            } else if (!expandedModes.remove(row.setting())) {
                                expandedModes.add(row.setting());
                            }
                        } else if (row.setting() instanceof BindSetting bind) {
                            binding = bind;
                            capturedBindKey = -1;
                            if (configPanel != null) configPanel.unfocus();
                        } else if (row.setting() instanceof IntSetting slider && y < rowY + row.pad() + SETTING_ROW_HEIGHT
                                && x >= settingAnimation(slider).valueLeft - 4) {
                            startEditingSlider(slider, x);
                        } else if (row.setting() instanceof SliderSetting slider) {
                            draggedSlider = slider;
                            sliderPanel = panel;
                            sliderX = rowX;
                            updateSlider(x);
                        } else {
                            row.setting().cycle();
                            settingAnimation(row.setting()).pulse = 1;
                            settingAnimation(row.setting()).pulseMotion.reset(1);
                        }
                    }
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private void startEditingSlider(IntSetting slider, float clickX) {
        if (configPanel != null) configPanel.unfocus();
        editingSlider = slider;
        sliderInput.setFocused(true);
        sliderInput.setValue(slider.typedValue());
        sliderClickX = clickX;
        sliderEditedAt = System.nanoTime();
    }

    private void stopEditingSlider(boolean apply) {
        if (editingSlider == null) return;
        if (apply) {
            try {
                editingSlider.setTyped(Double.parseDouble(sliderInput.getValue()));
                settingAnimation(editingSlider).pulse = 1;
                settingAnimation(editingSlider).pulseMotion.reset(1);
            } catch (NumberFormatException ignored) {
            }
        }
        editingSlider = null;
        sliderInput.setFocused(false);
    }

    private void updateSlider(float x) {
        float target = Math.clamp((x - sliderX - sliderInset()) / (panelWidth() - sliderInset() * 2), 0, 1);
        draggedSlider.setFraction(target);
        settingAnimation((Setting<?>) draggedSlider).sliderTarget = target;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (closing) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        float x = guiMouseX(event.x());
        float y = guiMouseY(event.y());
        if (draggingWindow) {
            windowX = x - dragX;
            windowY = y - dragY;
            layoutWindow();
            positionContent();
            return true;
        }
        if (draggedPreview != null) {
            if (expanded.contains(draggedPreview.module())) {
                playerPreviews.computeIfAbsent(draggedPreview, ignored -> new EspPlayerPreview())
                        .drag((float) deltaX / (scale() * windowZoom()),
                                (float) deltaY / (scale() * windowZoom()));
            }
            return true;
        }
        if (draggedColor != null) {
            updateColor(x, y);
            return true;
        }
        if (draggedSlider != null) {
            updateSlider(x);
            return true;
        }
        if (draggedPanel != null) {
            draggedPanel.targetX = x - dragX;
            draggedPanel.targetY = y - dragY;
            clampPanel(draggedPanel);
            return true;
        }
        return false;
    }

    private void clampPanel(Panel panel) {
        panel.targetX = Math.clamp(panel.targetX, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenWidth() - panelWidth() - SCREEN_MARGIN));
        panel.targetY = Math.clamp(panel.targetY, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight() - SCREEN_MARGIN));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_MIDDLE) describing = false;
        if (closing) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (draggedSlider != null) {
            updateSlider(guiMouseX(event.x()));
        }
        boolean handled = draggingWindow || draggedPanel != null || draggedSlider != null || draggedColor != null || draggedPreview != null;
        draggingWindow = false;
        draggedPreview = null;
        draggedPanel = null;
        draggedSlider = null;
        sliderPanel = null;
        draggedColor = null;
        colorPanel = null;
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (closing) return true;
        if (draggingWindow || draggedPanel != null) {
            targetResizeScale = Math.clamp(targetResizeScale * (float) Math.pow(1.1, vertical), 0.5f, 2.5f);
            return true;
        }
        float x = guiMouseX(mouseX);
        float y = guiMouseY(mouseY);
        if (selectedCategory == Category.CONFIG) return configPanel.mouseScrolled(x, y, vertical);
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            List<Row> rows = rows(panel);
            float height = visibleBodyHeight(panel, rows);
            if (!inside(x, y, panel.x, panel.y, panelSpan(), height)) {
                continue;
            }
            if (panel.open) {
                panel.targetScroll = Math.clamp(panel.targetScroll - (float) vertical * ROW_HEIGHT, 0, maxScroll(panel, rows));
            }
            return true;
        }
        return false;
    }

    private float animateLayout(SpringAnimation motion, float target, float speed) {
        return Math.clamp(motion.update(target, speed * 1.4f, 1, deltaTime), 0, 1);
    }

    private float animate(SpringAnimation motion, float target, float speed) {
        return motion.update(target, speed, deltaTime);
    }

    private ModuleAnimation moduleAnimation(Module module) {
        return moduleAnimations.computeIfAbsent(module, key -> new ModuleAnimation(module.isEnabled()));
    }

    private SettingAnimation settingAnimation(Setting<?> setting) {
        return settingAnimations.computeIfAbsent(setting, SettingAnimation::new);
    }

    public void tickClosedAnimations() {
        if (open) {
            return;
        }
        deltaTime = clock.nextDelta();
        updateAnimations();
    }

    private void updateAnimations() {
        for (Module module : manager.modules()) {
            ModuleAnimation animation = moduleAnimation(module);
            if (!open) {
                animation.hover = animate(animation.hoverMotion, 0, 18);
            }
            animation.open = Math.clamp(animation.openMotion.update(expanded.contains(module) ? 1 : 0, 28, 1, deltaTime), 0, 1);
            animation.enabled = animation.enabledMotion.update(module.isEnabled() ? 1 : 0, 13, 0.68f, deltaTime);

            for (Setting<?> setting : module.settings()) {
                SettingAnimation state = settingAnimation(setting);
                state.visibility = animate(state.visibilityMotion, setting.isVisible() ? 1 : 0, 28);
                if (!setting.isVisible()) {
                    expandedModes.remove(setting);
                    expandedColors.remove(setting);
                    if (binding == setting) {
                        binding = null;
                    }
                    if (draggedSlider == setting) {
                        draggedSlider = null;
                        sliderPanel = null;
                    }
                    if (draggedColor == setting) {
                        draggedColor = null;
                        colorPanel = null;
                    }
                }
                if (!open) {
                    state.hover = animate(state.hoverMotion, 0, 18);
                    state.modeHover.values().forEach(hover -> animate(hover, 0, 18));
                }
                state.dropdown = animateLayout(state.dropdownMotion,
                        (setting instanceof ChoiceSetting mode && expandedModes.contains(setting)
                                || setting instanceof ColorSetting color && expandedColors.contains(color)) ? 1 : 0, 20);
                if (state.dropdown == 0) {
                    state.modeHover.clear();
                }
                state.enabled = state.enabledMotion.update(setting instanceof BooleanSetting toggle && toggle.get() ? 1 : 0,
                        13, 0.68f, deltaTime);
                if (setting instanceof SliderSetting slider) {
                    float target = draggedSlider == slider ? state.sliderTarget : slider.fraction();
                    state.fraction = state.fractionMotion.update(target, draggedSlider == slider ? 32 : 26, deltaTime);
                    state.sliderInteraction = state.sliderInteractionMotion.update(
                            draggedSlider == slider ? 1 : Math.clamp(state.hover, 0, 1) * 0.45f, 22, 0.76f, deltaTime);
                }
                if (setting instanceof ColorSetting color) {

                    state.hue = animate(state.hueMotion, color.hue(), 24);
                    state.saturation = animate(state.saturationMotion, color.saturation(), 24);
                    state.brightness = animate(state.brightnessMotion, color.brightness(), 24);
                }
                state.pulse = animate(state.pulseMotion, 0, 12);
            }
        }
        for (Panel panel : panels) {
            panel.x = animate(panel.xMotion, panel.targetX, 35);
            panel.y = animate(panel.yMotion, panel.targetY, 35);
            panel.amount = animateLayout(panel.amountMotion, panel.open ? 1 : 0, 20);
            float maximum = maxScroll(panel, rows(panel));
            panel.targetScroll = Math.clamp(panel.targetScroll, 0, maximum);
            panel.scroll = animate(panel.scrollMotion, panel.targetScroll, 20);

            float clampedScroll = Math.clamp(panel.scroll, 0, maximum);
            if (clampedScroll != panel.scroll) panel.scrollMotion.reset(clampedScroll);
            panel.scroll = clampedScroll;
        }
    }

    private Panel panelAt(float x, float y) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (inside(x, y, panel.x, panel.y, panelSpan(), visibleBodyHeight(panel, rows(panel)))) {
                return panel;
            }
        }
        return null;
    }

    public Screen cleanViewBackground() {
        return cleanViewBackground;
    }

    public void cleanViewBackground(Screen screen) {
        cleanViewBackground = screen;
    }

    public void returnTo(Screen screen) {
        returnTo = screen;
    }

    @Override
    public void onClose() {
        if (!open || closing) return;
        closing = true;
        draggingWindow = false;
        draggedPanel = null;
        draggedSlider = null;
        sliderPanel = null;
        draggedColor = null;
        colorPanel = null;
        draggedPreview = null;
        binding = null;
        if (owner != null) owner.setEnabled(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (closing && windowPop.value() <= CLOSED && minecraft.gui.screen() == this) {
            minecraft.gui.setScreen(returnTo);
            returnTo = null;
        }
    }

    @Override
    public void removed() {
        stopEditingSlider(false);
        capturedBindKey = -1;
        if (configPanel != null) configPanel.unfocus();
        draggingWindow = false;
        draggedPreview = null;
        open = false;
        closing = false;
        windowPop.reset(0);
        binding = null;
        draggedPanel = null;
        draggedSlider = null;
        sliderPanel = null;
        draggedColor = null;
        colorPanel = null;
        if (owner != null) owner.setEnabled(false);
        super.removed();
    }

    public boolean reopen() {
        if (!open) return false;
        closing = false;
        return true;
    }

    public boolean isBinding() {
        return binding != null;
    }

    public boolean isTyping() {
        return binding != null || capturedBindKey != -1 || editingSlider != null
                || selectedCategory == Category.CONFIG && configPanel != null && configPanel.focused();
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) return true;
        if (event.key() == capturedBindKey) return true;
        if (editingSlider != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                stopEditingSlider(false);
            } else if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                stopEditingSlider(true);
            } else {
                sliderInput.keyPressed(event);
                sliderEditedAt = System.nanoTime();
            }
            return true;
        }
        if (binding != null) {
            capturedBindKey = event.key();
            boolean escape = event.key() == InputConstants.KEY_ESCAPE;
            if (!escape || owner == null || binding != owner.bind()) {
                boolean clear = escape || event.key() == InputConstants.KEY_BACKSPACE;
                binding.bind(clear ? -1 : event.key());
            }
            binding = null;
            return true;
        }
        if (selectedCategory == Category.CONFIG) {
            return configPanel.keyPressed(event) || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == capturedBindKey) {
            capturedBindKey = -1;
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (closing || binding != null || capturedBindKey != -1) return true;
        if (editingSlider != null) {
            int typed = event.codepoint();
            if (!Character.isDigit(typed) && typed != '.' && typed != '-' && typed != '+') return true;
            sliderEditedAt = System.nanoTime();
            return sliderInput.charTyped(event);
        }
        if (selectedCategory == Category.CONFIG && configPanel.focused()) {
            return configPanel.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
