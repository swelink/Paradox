package cc.swelink.gui.screens;

import cc.swelink.renderer.UiTokens;
import static cc.swelink.gui.screens.ClickGuiDraw.clip;
import static cc.swelink.gui.screens.ClickGuiDraw.fitLabel;
import static cc.swelink.gui.screens.ClickGuiDraw.inside;
import static cc.swelink.gui.screens.ClickGuiDraw.label;
import static cc.swelink.gui.screens.ClickGuiDraw.mix;

import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.GuiViewport;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.renderer.UiScale;
import cc.swelink.renderer.UniformGuiFade;
import cc.swelink.settings.ActionSetting;
import cc.swelink.settings.BindModeSetting;
import cc.swelink.settings.BindSetting;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.ChoiceSetting;
import cc.swelink.settings.ColorSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.Setting;
import cc.swelink.settings.SliderSetting;
import cc.swelink.systems.hud.HudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.ModuleManager;
import cc.swelink.systems.modules.combat.AutoAnchorModule;
import cc.swelink.systems.modules.render.DynamicIslandModule;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

public class ClickGuiScreen extends Screen {
    private static final Category[] CATEGORIES = {Category.COMBAT, Category.PLAYER, Category.RENDER,
            Category.MOVEMENT, Category.CLIENT, Category.CONFIG};

    private static final float PANEL_WIDTH = 185;
    private static final float PANEL_RADIUS = UiTokens.RADIUS_WINDOW;
    private static final float HEADER_HEIGHT = 32;
    private static final float PANEL_GAP = 14;
    private static final float ROW_HEIGHT = 28;
    private static final float KEY_FONT = UiTokens.TEXT_MICRO;
    private static final float KEY_PAD = 5;
    private static final float KEY_HEIGHT = 14;
    private static final int VISIBLE_MODULES = 18;
    private static final float TITLE_FONT = UiTokens.TEXT_TITLE;
    private static final float CATEGORY_ICON = 16;
    private static final float CATEGORY_ICON_GAP = 6;
    private static final float SCREEN_MARGIN = 8;
    private static final float BASE_SCALE = 0.8f;
    private static final float RESIZE_STEP = 0.1f;
    private static final float POP_SPEED = 38;
    private static final float CLOSED = 0.03f;

    private static final float EDGE = (ROW_HEIGHT - ClickGuiStyle.MODULE_FONT_SIZE * 0.73f) / 2;
    private static final float KEY_GAP = 7;
    private static final float ARROW_HALF = 3.5f;
    private static final float ARROW_RISE = 1.9f;
    private static final float ARROW_THICKNESS = 1.4f;
    private static final float ARROW_EXTRA_EDGE = 5;

    private static final float MODULE_FONT_SIZE = ClickGuiStyle.MODULE_FONT_SIZE;
    private static final float SETTING_FONT = ClickGuiStyle.SETTING_FONT_SIZE;
    private static final float FONT_SIZE = ClickGuiStyle.FONT_SIZE;
    private static final float SETTING_ROW_HEIGHT = ClickGuiStyle.SETTING_ROW_HEIGHT;
    private static final float SETTINGS_SPACING = ClickGuiStyle.SETTINGS_SPACING;

    private static final float SWITCH_WIDTH = 18;
    private static final float SWITCH_HEIGHT = 10;
    private static final float MODE_BOX_INSET = EDGE;
    private static final float MODE_TEXT_PAD = 5;
    private static final float MODE_BOX_RADIUS = UiTokens.RADIUS_CONTROL;
    private static final float MODE_ARROW_SPACE = 12;
    private static final float MODE_LABEL = 18;
    private static final float SLIDER_TRACK_Y = 27;
    private static final float SLIDER_TRACK = 4;
    private static final float SLIDER_KNOB = 5;
    private static final float CHIP_HEIGHT = 19;
    private static final float CHIP_PAD = 6;
    private static final float CHIP_GAP = 4;
    private static final float CHIP_RADIUS = UiTokens.RADIUS_CONTROL;
    private static final float CHIP_FONT = UiTokens.TEXT_MICRO;
    private static final float PICKER_RADIUS = UiTokens.RADIUS_CONTROL;
    private static final float COLOR_FIELD_HEIGHT = 80;
    private static final float HUE_GAP = 8;
    private static final float HUE_HEIGHT = 8;

    private static final float DESCRIPTION_FONT = UiTokens.TEXT_CAPTION;
    private static final float DESCRIPTION_PAD = 7;
    private static final float DESCRIPTION_WIDTH = 190;

    private static float resizeScale = 1;
    private static float targetResizeScale = 1;

    private final ModuleManager manager;
    private final Category[] categories;
    private final boolean overlay;
    private final Module owner;
    private final IntSupplier accent;
    private final TextRenderer measureText = new Renderer2D(null).text;
    private final FrameClock clock = new FrameClock();
    private final List<Panel> panels = new ArrayList<>();
    private final Set<Module> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Setting<?>> expandedModes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ColorSetting> expandedColors = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Module, ModuleAnimation> moduleAnimations = new IdentityHashMap<>();
    private final Map<Setting<?>, SettingAnimation> settingAnimations = new IdentityHashMap<>();
    private final SpringAnimation windowPop = new SpringAnimation();
    private final SpringAnimation descriptionReveal = new SpringAnimation();

    private ConfigPanel configPanel;
    private EditBox sliderInput;
    private Screen returnTo;
    private Screen cleanViewBackground;
    private float deltaTime;
    private float panelFade = 1;
    private boolean fadeIn;
    private boolean open;
    private boolean closing;
    private boolean describing;
    private Module describedModule;
    private Module shownDescription;
    private IntSetting editingSlider;
    private float sliderClickX = Float.NaN;
    private float sliderInputLeft;
    private float sliderInputRight;
    private float sliderInputCenterY;
    private long sliderEditedAt;
    private BindSetting binding;
    private int capturedBindKey = -1;
    private Panel draggedPanel;
    private float dragX;
    private float dragY;
    private SliderSetting draggedSlider;
    private float sliderX;
    private ColorSetting draggedColor;
    private Panel colorPanel;
    private boolean draggingHue;

    public ClickGuiScreen(ModuleManager manager, Module owner, IntSupplier accent) {
        this(manager, owner, accent, CATEGORIES, false);
    }

    ClickGuiScreen(ModuleManager manager, Module owner, IntSupplier accent, Category[] categories, boolean overlay) {
        super(Component.literal("Paradox"));
        this.categories = categories;
        this.overlay = overlay;
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
        if (opening) tickClosedAnimations();
        open = true;
        if (opening) {
            closing = false;
            capturedBindKey = -1;
            windowPop.reset(fadeIn ? 1 : 0);
        }
        panelFade = fadeIn ? 0 : 1;
        fadeIn = false;
        if (panels.isEmpty()) layoutPanels();
        for (Panel panel : panels) {
            clampPanel(panel);
            panel.x = panel.targetX;
            panel.y = panel.targetY;
        }
        clock.reset();
        Screen behind = screenBehind();
        if (behind != null && (behind.width != width || behind.height != height)) behind.resize(width, height);
        configPanel.resetClock();
    }

    private float scale() {
        return UiScale.of(minecraft) * resizeScale * BASE_SCALE;
    }

    private float screenWidth() {
        return GuiViewport.atScale(minecraft, scale()).width();
    }

    private float screenHeight() {
        return GuiViewport.atScale(minecraft, scale()).height();
    }

    private float maxBodyHeight() {
        return Math.max(ROW_HEIGHT * 2, Math.min(ROW_HEIGHT * VISIBLE_MODULES, screenHeight() - SCREEN_MARGIN * 2 - HEADER_HEIGHT));
    }

    private void layoutPanels() {
        float available = screenWidth() - SCREEN_MARGIN * 2;
        int perRow = Math.max(1, Math.min(categories.length, (int) ((available + PANEL_GAP) / (PANEL_WIDTH + PANEL_GAP))));
        float rowWidth = perRow * PANEL_WIDTH + (perRow - 1) * PANEL_GAP;
        float left = (screenWidth() - rowWidth) / 2;
        float top = Math.max(SCREEN_MARGIN, (screenHeight() - HEADER_HEIGHT - maxBodyHeight()) / 2);
        for (int i = 0; i < categories.length; i++) {
            float x = left + (i % perRow) * (PANEL_WIDTH + PANEL_GAP);
            float y = top + HEADER_HEIGHT + (i / perRow) * (HEADER_HEIGHT + maxBodyHeight() + PANEL_GAP);
            panels.add(new Panel(categories[i], x, y));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Screen behind = screenBehind();
        if (behind != null) {
            behind.extractBackground(graphics, -1, -1, partialTick);
            graphics.nextStratum();
            behind.extractRenderState(graphics, -1, -1, partialTick);
        } else if (minecraft.level == null) {
            extractPanorama(graphics, partialTick);
        }
    }

    private Screen screenBehind() {
        Screen behind = cleanViewBackground;
        return behind == null || behind == this || behind instanceof ClickGuiScreen || behind instanceof HudEditorScreen
                ? null : behind;
    }

    private float windowZoom() {
        return 0.9f + 0.1f * windowPop.value();
    }

    private float windowOpacity() {
        return FrameClock.smoothStep(Math.clamp(windowPop.value(), 0, 1));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        deltaTime = clock.nextDelta();
        float oldWidth = screenWidth();
        float oldHeight = screenHeight();
        resizeScale = FrameClock.approach(resizeScale, targetResizeScale, 18, deltaTime);
        float shiftX = (screenWidth() - oldWidth) / 2;
        float shiftY = (screenHeight() - oldHeight) / 2;
        if (shiftX != 0 || shiftY != 0) {
            for (Panel panel : panels) {
                panel.targetX += shiftX;
                panel.targetY += shiftY;
                clampPanel(panel);
                panel.x = panel.targetX;
                panel.y = panel.targetY;
            }
        }
        updateAnimations();
        windowPop.update(closing ? 0 : 1, POP_SPEED, 1, deltaTime);
        panelFade = Math.min(1, panelFade + deltaTime / 0.07f);

        float x = guiMouseX(mouseX);
        float y = guiMouseY(mouseY);
        if (draggedPanel != null) {
            draggedPanel.targetX = x - dragX;
            draggedPanel.targetY = y - dragY;
            clampPanel(draggedPanel);
        }

        Panel hoveredPanel = panelAt(x, y);
        graphics.nextStratum();
        UniformGuiFade.begin(graphics, windowOpacity());

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale());
        try (Renderer2D renderer = new Renderer2D(graphics, TextRenderer.Font.INTER)) {
            float centerX = screenWidth() / 2;
            float centerY = screenHeight() / 2;
            graphics.pose().translate(centerX, centerY);
            graphics.pose().scale(windowZoom());
            graphics.pose().translate(-centerX, -centerY);
            float opacity = FrameClock.smoothStep(panelFade);
            for (Panel panel : panels) {
                boolean top = panel == hoveredPanel;
                drawPanel(renderer, panel, top ? x : -1, top ? y : -1, opacity);
            }
            drawDescription(renderer, x, y);
        } finally {
            graphics.pose().popMatrix();
        }
        UniformGuiFade.end(graphics);
    }

    private float guiMouseX(double x) {
        float center = screenWidth() / 2;
        return center + ((float) x / scale() - center) / windowZoom();
    }

    private float guiMouseY(double y) {
        float center = screenHeight() / 2;
        return center + ((float) y / scale() - center) / windowZoom();
    }

    private int accentColor() {
        return ARGB.opaque(ThemeColors.primary());
    }

    private int bodyColor() {
        return ClickGuiStyle.PANEL_BODY;
    }

    private int settingsColor() {
        return ClickGuiStyle.PANEL_INSET;
    }

    private int textColor() {
        return ClickGuiStyle.MODULE_TEXT;
    }

    private float colorFieldWidth() {
        return PANEL_WIDTH - EDGE * 2;
    }

    private static boolean inHudEditor(Module module) {
        return module instanceof HudElement && !(module instanceof DynamicIslandModule);
    }

    public void carryWindow() {
        fadeIn = true;
    }

    public void openHudEditor() {
        Screen back = returnTo;
        returnTo = null;
        minecraft.gui.setScreen(new HudEditorScreen(manager, accent, back, true));
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
        float x = Math.min(mouseX + 10, screenWidth() - width - 4);
        float y = mouseY + 12;
        if (y + height > screenHeight() - 4) y = mouseY - height - 6;
        float baseOpacity = renderer.opacity();
        renderer.opacity(baseOpacity * reveal);
        try {
            renderer.sdf.roundedRect(x, y + 4 * (1 - reveal), width, height, ClickGuiStyle.CARD_RADIUS - 2, ClickGuiStyle.raised());
            renderer.text.drawWrapped(text, x + DESCRIPTION_PAD, y + 4 * (1 - reveal) + DESCRIPTION_PAD, DESCRIPTION_FONT,
                    DESCRIPTION_WIDTH - DESCRIPTION_PAD * 2, textColor());
        } finally {
            renderer.opacity(baseOpacity);
        }
    }

    private void drawPanel(Renderer2D renderer, Panel panel, float mouseX, float mouseY, float opacity) {
        List<Row> rows = rows(panel);
        float body = visibleBodyHeight(panel, rows);
        float top = panel.y - HEADER_HEIGHT;
        float height = HEADER_HEIGHT + body;
        renderer.opacity(opacity);
        try {
            renderer.sdf.roundedRect(panel.x, top, PANEL_WIDTH, height, PANEL_RADIUS, bodyColor());
            renderer.sdf.clippedRoundedRect(panel.x, top, PANEL_WIDTH, height, PANEL_RADIUS, ClickGuiStyle.PANEL_HEADER,
                    panel.x, top, panel.x + PANEL_WIDTH, panel.y);
            float centerY = top + HEADER_HEIGHT / 2;
            float iconX = panel.x + EDGE;
            renderer.textures.icon(panel.category.icon(), iconX, centerY - CATEGORY_ICON / 2, CATEGORY_ICON, ClickGuiStyle.MODULE_TEXT);
            var title = renderer.text.withFont(TextRenderer.Font.INTER_MEDIUM);
            title.draw(panel.category.title(), iconX + CATEGORY_ICON + CATEGORY_ICON_GAP,
                    centerY - title.lineHeight(TITLE_FONT) / 2, TITLE_FONT, ClickGuiStyle.TITLE);
            drawChevron(renderer, arrowCenter(panel.x), centerY, ARROW_HALF, ARROW_RISE, ARROW_THICKNESS,
                    panel.amount, ClickGuiStyle.ARROW);
            if (body > 0.5f) {
                try (var clip = clip(renderer, panel.x - 1, panel.y - 1, PANEL_WIDTH + 2, body + 2)) {
                    if (panel.category == Category.CONFIG) {
                        configPanel.shape(top, height, PANEL_RADIUS);
                        configPanel.render(renderer, panel.x, panel.y, PANEL_WIDTH, body, mouseX, mouseY, accent.getAsInt());
                    } else {
                        drawBody(renderer, panel, rows, body, mouseX, mouseY);
                    }
                }
            }
        } finally {
            renderer.opacity(1);
        }
    }

    private void drawBody(Renderer2D renderer, Panel panel, List<Row> rows, float body, float mouseX, float mouseY) {
        if (rows.isEmpty()) {
            label(renderer, "Empty", panel.x + EDGE, panel.y + ROW_HEIGHT / 2,
                    MODULE_FONT_SIZE, ClickGuiStyle.muted());
            return;
        }
        drawRowStrips(renderer, panel, rows, body);
        float top = panel.y;
        float bottom = panel.y + body;
        try (var clip = clip(renderer, panel.x, top, PANEL_WIDTH, body)) {
            for (Block block : blocks(rows)) {
                float y = panel.y - panel.scroll + block.y();
                if (y + block.height() <= top || y >= bottom) continue;
                drawModeBoxes(renderer, rows, block, panel.x, y);
                float rowY = y;
                for (int i = block.start(); i < block.end(); i++) {
                    Row row = rows.get(i);
                    if (rowY + row.height() > top && rowY < bottom) {
                        boolean hovered = inside(mouseX, mouseY, panel.x, rowY, PANEL_WIDTH, row.height())
                                && inside(mouseX, mouseY, panel.x, top, PANEL_WIDTH, body);
                        drawRow(renderer, row, panel.x, rowY, hovered, top, bottom);
                    }
                    rowY += row.height();
                }
            }
        }
    }

    private record Block(int start, int end, float y, float height) {
    }

    private record Placed(Row row, float y) {
    }

    private List<Block> blocks(List<Row> rows) {
        List<Block> blocks = new ArrayList<>();
        float y = 0;
        int index = 0;
        while (index < rows.size()) {
            Module module = rows.get(index).module();
            int end = index;
            float height = 0;
            while (end < rows.size() && rows.get(end).module() == module) {
                height += rows.get(end).height();
                end++;
            }
            blocks.add(new Block(index, end, y, height));
            y += height;
            index = end;
        }
        return blocks;
    }

    private List<Placed> placedRows(Panel panel, List<Row> rows) {
        List<Placed> placed = new ArrayList<>();
        float y = panel.y - panel.scroll;
        for (Row row : rows) {
            placed.add(new Placed(row, y));
            y += row.height();
        }
        return placed;
    }

    private void drawRowStrips(Renderer2D renderer, Panel panel, List<Row> rows, float body) {
        float shapeTop = panel.y - HEADER_HEIGHT;
        float shapeHeight = HEADER_HEIGHT + body;
        float left = panel.x;
        float right = panel.x + PANEL_WIDTH;
        float viewTop = panel.y;
        float viewBottom = panel.y + body;
        for (Block block : blocks(rows)) {
            float y = panel.y - panel.scroll + block.y();
            float headerBottom = y + rows.get(block.start()).height();
            float bottom = y + block.height();
            if (bottom <= viewTop || y >= viewBottom) continue;
            float settingsTop = Math.max(headerBottom, viewTop);
            float settingsBottom = Math.min(bottom, viewBottom);
            if (settingsBottom > settingsTop) {
                renderer.sdf.clippedRoundedRect(left, shapeTop, PANEL_WIDTH, shapeHeight, PANEL_RADIUS,
                        settingsColor(), left, settingsTop, right, settingsBottom);
            }
        }
    }

    private void drawModeBoxes(Renderer2D renderer, List<Row> rows, Block block, float x, float y) {
        float groupY = y;
        for (int i = block.start(); i < block.end(); i++) {
            Row row = rows.get(i);
            if (row.setting() instanceof ChoiceSetting && !chips(row.setting()) && !row.spacer() && row.groupOffset() == 0) {
                float boxX = x + MODE_BOX_INSET;
                float boxWidth = PANEL_WIDTH - MODE_BOX_INSET * 2;
                float boxTop = groupY + MODE_LABEL * row.amount();
                float boxHeight = row.groupHeight() - MODE_LABEL * row.amount();
                renderer.sdf.roundedRect(boxX, boxTop, boxWidth, boxHeight, MODE_BOX_RADIUS,
                        ClickGuiStyle.CONTROL);
                float optionsTop = groupY + row.height();
                if (row.groupHeight() > row.height() + 0.5f) {
                    renderer.sdf.clippedRoundedRect(boxX, boxTop, boxWidth, boxHeight, MODE_BOX_RADIUS,
                            ClickGuiStyle.CONTROL_DARK, boxX, optionsTop, boxX + boxWidth,
                            groupY + row.groupHeight());
                }
            }
            groupY += row.height();
        }
    }

    private void drawRow(Renderer2D renderer, Row row, float x, float y, boolean hovered, float viewportTop, float viewportBottom) {
        float active = row.setting() != null ? Math.clamp(settingAnimation(row.setting()).visibility, 0, 1) : 1;
        drawRowContent(renderer, row, x, y, hovered && active > 0.95f, viewportTop, viewportBottom);
        if (active >= 0.999f) return;
        float top = Math.max(y, viewportTop);
        float bottom = Math.min(y + row.height(), viewportBottom);
        if (bottom <= top) return;
        float left = x + MODE_BOX_INSET - 2;
        float width = PANEL_WIDTH - (MODE_BOX_INSET - 2) * 2;
        int veil = settingsColor();
        renderer.sdf.clippedRoundedRect(left, y, width, row.height(), 0,
                ARGB.color(Math.round(255 * (1 - active)), veil), left, top, left + width, bottom);
    }

    private void drawRowContent(Renderer2D renderer, Row row, float x, float y, boolean hovered,
                                float viewportTop, float viewportBottom) {
        if (row.spacer()) return;
        float top = Math.max(y, viewportTop);
        float bottom = Math.min(y + row.height(), viewportBottom);
        Setting<?> setting = row.setting();
        ModuleAnimation moduleAnimation = moduleAnimation(row.module());
        SettingAnimation settingAnimation = setting == null ? null : settingAnimation(setting);
        float hover;
        if (setting == null) {
            moduleAnimation.hover = moduleAnimation.hoverMotion.update(hovered ? 1 : 0, 20, 1, deltaTime);
            hover = moduleAnimation.hover;
        } else if (row.modeChoice() != null) {
            hover = animate(settingAnimation.modeHover.computeIfAbsent(row.modeChoice(), ignored -> new SpringAnimation()),
                    hovered ? 1 : 0, 18);
        } else {
            settingAnimation.hover = animate(settingAnimation.hoverMotion, hovered ? 1 : 0, 18);
            hover = settingAnimation.hover;
        }

        try (var rowClip = clip(renderer, x, top, PANEL_WIDTH, bottom - top)) {
            if (setting == null) {
                if (hovered) describedModule = row.module();
                drawModuleHeader(renderer, row, moduleAnimation, x, y);
            } else if (row.colorPicker()) {
                drawColorPicker(renderer, (ColorSetting) setting, settingAnimation, x, y);
            } else if (row.modeChoice() != null) {
                boolean selected = ((ChoiceSetting) setting).selectedChoice().equals(row.modeChoice());
                label(renderer, row.modeChoice(), x + EDGE + MODE_TEXT_PAD, y + SETTING_ROW_HEIGHT / 2, SETTING_FONT,
                        selected ? accentColor() : mix(ClickGuiStyle.LABEL, textColor(), hover));
            } else if (setting instanceof ActionSetting) {
                label(renderer, setting.name(), x + EDGE, y + SETTING_ROW_HEIGHT / 2,
                        SETTING_FONT, AccentWave.text(ClickGuiStyle.text(), hover));
                label(renderer, "›", x + PANEL_WIDTH - EDGE - 5, y + SETTING_ROW_HEIGHT / 2, FONT_SIZE, AccentWave.text());
            } else {
                drawSetting(renderer, setting, settingAnimation, x, y);
            }
        }
    }

    private void drawChevron(Renderer2D renderer, float centerX, float centerY, float half, float rise, float thickness,
                             float open, int color) {
        double angle = Math.PI * Math.clamp(open, 0, 1);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float[][] points = {{-half, -rise}, {0, rise}, {half, -rise}};
        float[][] turned = new float[3][];
        for (int i = 0; i < 3; i++) {
            float px = points[i][0];
            float py = points[i][1];
            turned[i] = new float[] {centerX + px * cos - py * sin, centerY + px * sin + py * cos};
        }
        renderer.sdf.roundLine(turned[0][0], turned[0][1], turned[1][0], turned[1][1], thickness, color);
        renderer.sdf.roundLine(turned[1][0], turned[1][1], turned[2][0], turned[2][1], thickness, color);
    }

    private float arrowCenter(float rowX) {
        return rowX + PANEL_WIDTH - EDGE - ARROW_EXTRA_EDGE - ARROW_HALF;
    }

    private void drawModuleHeader(Renderer2D renderer, Row row, ModuleAnimation animation, float x, float y) {
        float centerY = y + row.height() / 2;
        float enabled = Math.clamp(animation.enabled, 0, 1);
        float hover = Math.clamp(animation.hover, 0, 1);
        float nameX = x + EDGE;
        int color = mix(mix(textColor(), ClickGuiStyle.MODULE_HOVER, hover), accentColor(), enabled);
        drawChevron(renderer, arrowCenter(x), centerY, ARROW_HALF, ARROW_RISE, ARROW_THICKNESS, animation.open,
                mix(ClickGuiStyle.ARROW, accentColor(), enabled));
        float right = arrowCenter(x) - ARROW_HALF - KEY_GAP;
        if (row.module().bind().get() != -1) {
            String key = fitLabel(renderer, row.module().bind().displayValue(), KEY_FONT, 40);
            float keyWidth = renderer.text.measure(key, KEY_FONT).width() + KEY_PAD * 2;
            float keyLeft = right - keyWidth;
            renderer.sdf.roundedRect(keyLeft, centerY - KEY_HEIGHT / 2, keyWidth, KEY_HEIGHT, 3,
                    ClickGuiStyle.CONTROL);
            label(renderer, key, keyLeft + KEY_PAD, centerY, KEY_FONT, ClickGuiStyle.KEY_TEXT);
            right = keyLeft - KEY_GAP;
        }
        String name = fitLabel(renderer.text, row.module().name(), MODULE_FONT_SIZE, right - nameX - 4);
        renderer.text.draw(name, nameX, centerY - renderer.text.lineHeight(MODULE_FONT_SIZE) / 2, MODULE_FONT_SIZE, color);
    }

    private String getSettingValue(Setting<?> setting) {
        if (setting == binding) return "Press input...";
        if (setting instanceof BooleanSetting || setting instanceof ColorSetting) return "";
        if (setting instanceof IntSetting slider) return slider.displayValue(settingAnimation(setting).fraction);
        return setting.displayValue();
    }

    private void drawSwitch(Renderer2D renderer, float right, float centerY, float amount) {
        float left = right - SWITCH_WIDTH;
        float on = Math.clamp(amount, 0, 1);
        int track = mix(ClickGuiStyle.TRACK, ARGB.opaque(ThemeColors.primary()), on);
        renderer.sdf.roundedRect(left, centerY - SWITCH_HEIGHT / 2, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2, track);
        float travel = SWITCH_WIDTH - SWITCH_HEIGHT;
        float knobX = left + SWITCH_HEIGHT / 2 + travel * Math.clamp(amount, -0.1f, 1.1f);
        renderer.sdf.circle(knobX, centerY, SWITCH_HEIGHT / 2 - 1.5f,
                mix(ClickGuiStyle.LABEL, ClickGuiStyle.TITLE, on));
    }

    private boolean chips(Setting<?> setting) {
        if (!(setting instanceof BindModeSetting choice)) return false;
        float width = chipWidth(choice);
        for (String option : choice.choices()) {
            if (measureText.measure(option, CHIP_FONT).width() + CHIP_PAD * 2 > width) return false;
        }
        return true;
    }

    private float chipWidth(ChoiceSetting choice) {
        int count = Math.max(1, choice.choices().size());
        return (PANEL_WIDTH - EDGE * 2 - CHIP_GAP * (count - 1)) / count;
    }

    private float chipLeft(ChoiceSetting choice, float rowX, int index) {
        return rowX + EDGE + index * (chipWidth(choice) + CHIP_GAP);
    }

    private void drawChips(Renderer2D renderer, ChoiceSetting choice, float x, float centerY) {
        List<String> options = choice.choices();
        float width = chipWidth(choice);
        float top = centerY - CHIP_HEIGHT / 2;
        int selectedIndex = Math.max(0, options.indexOf(choice.selectedChoice()));
        SpringAnimation motion = settingAnimation((Setting<?>) choice).chipMotion;
        if (motion.value() < 0) motion.reset(selectedIndex);
        float position = motion.update(selectedIndex, 26, 1, deltaTime);
        for (int i = 0; i < options.size(); i++) {
            renderer.sdf.roundedRect(chipLeft(choice, x, i), top, width, CHIP_HEIGHT, CHIP_RADIUS,
                    ClickGuiStyle.CONTROL);
        }
        float highlightX = x + EDGE + Math.clamp(position, 0, options.size() - 1) * (width + CHIP_GAP);
        renderer.sdf.roundedRect(highlightX, top, width, CHIP_HEIGHT, CHIP_RADIUS, ARGB.opaque(ThemeColors.primary()));
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            float active = Math.clamp(1 - Math.abs(position - i), 0, 1);
            float textWidth = renderer.text.measure(option, CHIP_FONT).width();
            label(renderer, option, chipLeft(choice, x, i) + (width - textWidth) / 2, centerY, CHIP_FONT,
                    mix(ClickGuiStyle.LABEL, textColor(), active));
        }
    }

    private void drawSetting(Renderer2D renderer, Setting<?> setting, SettingAnimation animation, float x, float y) {
        float centerY = y + SETTING_ROW_HEIGHT / 2;
        if (chips(setting)) {
            drawChips(renderer, (ChoiceSetting) setting, x, centerY);
            return;
        }
        float left = x + EDGE;
        float right = x + PANEL_WIDTH - EDGE;
        int text = textColor();
        int primary = ARGB.opaque(ThemeColors.primary());
        if (setting instanceof ChoiceSetting) {
            label(renderer, fitLabel(renderer, setting.name(), SETTING_FONT, right - left), left, y + MODE_LABEL / 2,
                    SETTING_FONT, ClickGuiStyle.LABEL);
            float boxCenter = y + MODE_LABEL + SETTING_ROW_HEIGHT / 2;
            label(renderer, fitLabel(renderer, getSettingValue(setting), SETTING_FONT,
                    right - left - MODE_TEXT_PAD * 2 - MODE_ARROW_SPACE), left + MODE_TEXT_PAD, boxCenter, SETTING_FONT, text);
            drawChevron(renderer, right - MODE_TEXT_PAD - 3.5f, boxCenter, 3.5f, 1.9f, 1.3f, animation.dropdown, text);
            return;
        }
        boolean editing = setting == editingSlider;
        String value = setting instanceof BooleanSetting || setting instanceof ColorSetting ? ""
                : fitLabel(renderer, getSettingValue(setting), SETTING_FONT, (right - left) * 0.5f);
        float valueWidth = renderer.text.measure(value, SETTING_FONT).width();
        float reserved = setting instanceof BooleanSetting ? SWITCH_WIDTH + 6
                : setting instanceof ColorSetting ? 14
                : editing ? renderer.text.measure(sliderInput.getValue() + editingSlider.typedSuffix(), SETTING_FONT).width() + 6
                : valueWidth + 6;
        float lit = setting instanceof BooleanSetting ? Math.clamp(animation.enabled, 0, 1) : 0;
        label(renderer, fitLabel(renderer, setting.name(), SETTING_FONT, right - left - reserved), left, centerY, SETTING_FONT,
                mix(ClickGuiStyle.LABEL, textColor(), lit));
        if (setting instanceof BooleanSetting) {
            drawSwitch(renderer, right, centerY, animation.enabled);
        } else if (setting instanceof ColorSetting color) {
            renderer.sdf.circle(right - 4, centerY, 4, ARGB.opaque(color.get()));
        } else if (editing) {
            drawSliderInput(renderer, right, centerY);
        } else {
            animation.valueLeft = right - valueWidth;
            label(renderer, value, animation.valueLeft, centerY, SETTING_FONT,
                    setting instanceof BindSetting ? ClickGuiStyle.muted() : accentColor());
        }
        if (setting instanceof SliderSetting) {
            float width = right - left;
            float trackY = y + SLIDER_TRACK_Y;
            float fill = width * Math.clamp(animation.fraction, 0, 1);
            float half = SLIDER_TRACK / 2;
            renderer.sdf.roundedRect(left, trackY - half, width, SLIDER_TRACK, half, ClickGuiStyle.TRACK);
            if (fill > 0) renderer.sdf.roundedRect(left, trackY - half, fill, SLIDER_TRACK, half, primary);
            renderer.sdf.circle(Math.clamp(left + fill, left + SLIDER_KNOB, right - SLIDER_KNOB), trackY, SLIDER_KNOB, text);
        }
    }

    private void drawSliderInput(Renderer2D renderer, float right, float centerY) {
        String typed = sliderInput.getValue();
        String suffix = editingSlider.typedSuffix();
        float numberRight = right - renderer.text.measure(suffix, SETTING_FONT).width();
        float left = numberRight - renderer.text.measure(typed, SETTING_FONT).width();
        sliderInputLeft = left;
        sliderInputRight = right;
        sliderInputCenterY = centerY;
        if (!Float.isNaN(sliderClickX)) {
            int closest = 0;
            float nearest = Float.MAX_VALUE;
            for (int index = 0; index <= typed.length(); index++) {
                float stop = left + renderer.text.measure(typed.substring(0, index), SETTING_FONT).width();
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
        float caret = left + renderer.text.measure(typed.substring(0, cursor), SETTING_FONT).width();
        String selected = sliderInput.getHighlighted();
        if (!selected.isEmpty()) {
            int end = cursor + selected.length();
            if (end > typed.length() || !typed.substring(cursor, end).equals(selected)) end = cursor - selected.length();
            float other = left + renderer.text.measure(typed.substring(0, end), SETTING_FONT).width();
            renderer.sdf.roundedRect(Math.min(caret, other), centerY - 8, Math.abs(other - caret), 16, 3,
                    ARGB.color(0x50, accent.getAsInt()));
        }
        label(renderer, typed, left, centerY, SETTING_FONT, textColor());
        label(renderer, suffix, numberRight, centerY, SETTING_FONT, textColor());
        float idle = (System.nanoTime() - sliderEditedAt) * 1e-9f;
        if (idle < 0.5f || idle % 1 < 0.5f) {
            renderer.sdf.roundedRect(caret, centerY - 7, 1, 14, 0.5f, accent.getAsInt());
        }
    }

    private void drawColorPicker(Renderer2D renderer, ColorSetting color, SettingAnimation animation, float x, float y) {
        float left = x + EDGE;
        float top = y + 4;
        float width = colorFieldWidth();
        int hue = ColorSetting.rgb(color.hue(), 1, 1);
        renderer.colorField(left, top, width, COLOR_FIELD_HEIGHT, PICKER_RADIUS, hue);
        float hueTop = top + COLOR_FIELD_HEIGHT + HUE_GAP;
        renderer.hueBar(left, hueTop, width, HUE_HEIGHT);
        float cursorX = Math.clamp(left + animation.saturation * width, left + 5, left + width - 5);
        float cursorY = Math.clamp(top + (1 - animation.brightness) * COLOR_FIELD_HEIGHT, top + 5, top + COLOR_FIELD_HEIGHT - 5);
        renderer.sdf.circle(cursorX, cursorY, 4, ClickGuiStyle.header());
        renderer.sdf.circle(cursorX, cursorY, 3, ClickGuiStyle.text());
        float hueX = left + animation.hue * width;
        renderer.sdf.circle(hueX, hueTop + HUE_HEIGHT / 2, 5, ClickGuiStyle.header());
        renderer.sdf.circle(hueX, hueTop + HUE_HEIGHT / 2, 4, ClickGuiStyle.text());
    }

    private void updateColor(float x, float y) {
        for (Placed placed : placedRows(colorPanel, rows(colorPanel))) {
            Row row = placed.row();
            if (row.setting() == draggedColor && row.colorPicker()) {
                float position = Math.clamp((x - colorPanel.x - EDGE) / colorFieldWidth(), 0, 1);
                if (draggingHue) {
                    draggedColor.select(position, draggedColor.saturation(), draggedColor.brightness());
                } else {
                    float vertical = Math.clamp((y - placed.y() - 4) / COLOR_FIELD_HEIGHT, 0, 1);
                    draggedColor.select(draggedColor.hue(), position, 1 - vertical);
                }
                return;
            }
        }
    }

    private boolean belongs(Panel panel, Module module) {
        if (panel.category == Category.CONFIG) return false;
        if (panel.category == Category.HUD) return inHudEditor(module);
        return !inHudEditor(module) && module.category() == panel.category;
    }

    private List<Row> rows(Panel panel) {
        List<Row> rows = new ArrayList<>();
        for (Module module : manager.modules()) {
            if (module instanceof DynamicIslandModule || !belongs(panel, module)) continue;
            rows.add(Row.module(module, ROW_HEIGHT));
            float amount = moduleAnimation(module).open;
            if (amount <= 0) continue;
            List<Row> settingsRows = settingsRows(module);
            float settingsHeight = 0;
            for (Row row : settingsRows) settingsHeight += row.height();
            float padding = SETTINGS_SPACING * 2;
            float visibleHeight = (settingsHeight + padding) * amount;
            float spacingAmount = Math.min(1, visibleHeight / padding);
            rows.add(Row.spacing(module, spacingAmount));
            float remaining = Math.max(0, visibleHeight - padding);
            for (Row row : settingsRows) {
                float rowHeight = row.height();
                float reveal = Math.clamp(remaining / rowHeight, 0, 1);
                if (reveal > 0) {
                    float groupHeight = Math.min(row.groupHeight(), remaining + row.groupOffset());
                    rows.add(row.copy(row.amount() * reveal, row.groupOffset(), groupHeight));
                }
                remaining -= rowHeight;
            }
            rows.add(Row.spacing(module, spacingAmount + Math.max(0, remaining) / SETTINGS_SPACING));
        }
        return rows;
    }

    private List<Row> settingsRows(Module module) {
        List<Row> rows = new ArrayList<>();
        List<Row> group = new ArrayList<>();
        for (Setting<?> setting : module.settings()) {
            float visibility = settingAnimation(setting).visibility;
            if (visibility == 0) continue;
            if (setting instanceof ColorSetting color) {
                appendSettingsGroup(rows, group, module);
                group.clear();
                group.add(new Row(module, setting, visibility));
                float reveal = settingAnimation(color).dropdown * visibility;
                if (reveal > 0) group.add(new Row(module, setting, reveal, 0, 0, false, null, true));
                appendSettingsGroup(rows, group, module);
                group.clear();
            } else if (chips(setting)) {
                group.add(new Row(module, setting, visibility));
            } else if (setting instanceof ChoiceSetting mode) {
                appendSettingsGroup(rows, group, module);
                group.clear();
                group.add(new Row(module, setting, visibility).sized(MODE_LABEL + SETTING_ROW_HEIGHT));
                float remaining = mode.choices().size() * SETTING_ROW_HEIGHT * settingAnimation(setting).dropdown * visibility;
                for (String choice : mode.choices()) {
                    float reveal = Math.clamp(remaining / SETTING_ROW_HEIGHT, 0, 1);
                    if (reveal > 0) group.add(new Row(module, setting, reveal, 0, 0, false, choice, false));
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
        if (group.isEmpty()) return;
        if (!rows.isEmpty()) {
            float visibility = 0;
            for (Row row : group) visibility = Math.max(visibility, row.amount());
            rows.add(Row.spacing(module, visibility));
        }
        float height = 0;
        for (Row row : group) height += row.height();
        float offset = 0;
        for (Row row : group) {
            rows.add(row.copy(row.amount(), offset, height));
            offset += row.height();
        }
    }

    private float contentHeight(List<Row> rows) {
        float height = 0;
        for (Row row : rows) height += row.height();
        return Math.max(ROW_HEIGHT, height);
    }

    private float bodyHeight(Panel panel, List<Row> rows) {
        float limit = Math.max(ROW_HEIGHT * 2, Math.min(maxBodyHeight(), screenHeight() - SCREEN_MARGIN - panel.y));
        float content = panel.category == Category.CONFIG ? configPanel.contentHeight() : contentHeight(rows);
        return Math.min(content, limit);
    }

    private float visibleBodyHeight(Panel panel, List<Row> rows) {
        return bodyHeight(panel, rows) * panel.amount;
    }

    private float maxScroll(Panel panel, List<Row> rows) {
        return Math.max(0, contentHeight(rows) - bodyHeight(panel, rows));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) return true;
        float x = guiMouseX(event.x());
        float y = guiMouseY(event.y());
        if (editingSlider != null) {
            if (inside(x, y, sliderInputLeft - 4, sliderInputCenterY - 10, sliderInputRight - sliderInputLeft + 8, 20)) {
                sliderClickX = x;
                sliderEditedAt = System.nanoTime();
                return true;
            }
            stopEditingSlider(true);
        }
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
        if (button == InputConstants.MOUSE_BUTTON_LEFT) configPanel.unfocus();
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            List<Row> rows = rows(panel);
            float height = visibleBodyHeight(panel, rows);
            if (!inside(x, y, panel.x, panel.y - HEADER_HEIGHT, PANEL_WIDTH, HEADER_HEIGHT + height)) continue;
            panels.remove(i);
            panels.add(panel);
            if (y < panel.y) {
                if (button == InputConstants.MOUSE_BUTTON_RIGHT
                        || button == InputConstants.MOUSE_BUTTON_LEFT && Math.abs(x - arrowCenter(panel.x)) <= ARROW_HALF + 4) {
                    panel.open = !panel.open;
                } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                    draggedPanel = panel;
                    dragX = x - panel.x;
                    dragY = y - panel.y;
                }
                return true;
            }
            if (!panel.open) return true;
            if (panel.category == Category.CONFIG) {
                configPanel.mouseClicked(x, y, button);
                return true;
            }
            for (Placed placed : placedRows(panel, rows)) {
                Row row = placed.row();
                float rowY = placed.y();
                if (y >= rowY && y < rowY + row.height()) {
                    clickRow(row, panel, rowY, x, y, button);
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private void clickRow(Row row, Panel panel, float rowY, float x, float y, int button) {
        if (row.spacer()) return;
        Module module = row.module();
        if (row.setting() == null) {
            boolean onArrow = !module.settings().isEmpty() && Math.abs(x - arrowCenter(panel.x)) <= ARROW_HALF + 4;
            if (button == InputConstants.MOUSE_BUTTON_LEFT && !onArrow) {
                module.toggle();
            } else if (!module.settings().isEmpty()
                    && (button == InputConstants.MOUSE_BUTTON_RIGHT || button == InputConstants.MOUSE_BUTTON_LEFT)) {
                if (!expanded.remove(module)) expanded.add(module);
            }
            return;
        }
        Setting<?> setting = row.setting();
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !expanded.contains(module) || !setting.isVisible()
                || settingAnimation(setting).visibility <= 0.95f || moduleAnimation(module).open <= 0.95f) {
            return;
        }
        if (setting instanceof ColorSetting color) {
            if (!row.colorPicker()) {
                if (!expandedColors.remove(color)) expandedColors.add(color);
                return;
            }
            if (settingAnimation(color).dropdown < 0.95f || !expandedColors.contains(color)) return;
            float fieldX = panel.x + EDGE;
            float hueTop = rowY + 4 + COLOR_FIELD_HEIGHT + HUE_GAP;
            if (inside(x, y, fieldX, rowY + 4, colorFieldWidth(), COLOR_FIELD_HEIGHT)
                    || inside(x, y, fieldX, hueTop, colorFieldWidth(), HUE_HEIGHT)) {
                draggedColor = color;
                colorPanel = panel;
                draggingHue = y >= hueTop;
                updateColor(x, y);
            }
        } else if (setting instanceof ChoiceSetting mode && chips(setting)) {
            for (int chip = 0; chip < mode.choices().size(); chip++) {
                float chipX = chipLeft(mode, panel.x, chip);
                if (x >= chipX && x < chipX + chipWidth(mode)) mode.selectChoice(mode.choices().get(chip));
            }
        } else if (setting instanceof ChoiceSetting mode) {
            if (row.modeChoice() == null) {
                if (!expandedModes.remove(setting)) expandedModes.add(setting);
            } else if (settingAnimation(setting).dropdown >= 0.95f && expandedModes.contains(setting)) {
                mode.selectChoice(row.modeChoice());
                expandedModes.remove(setting);
            }
        } else if (setting instanceof BindSetting bind) {
            binding = bind;
            capturedBindKey = -1;
            configPanel.unfocus();
        } else if (setting instanceof IntSetting slider && y < rowY + SETTING_ROW_HEIGHT
                && x >= settingAnimation(slider).valueLeft - 4) {
            startEditingSlider(slider, x);
        } else if (setting instanceof SliderSetting slider) {
            draggedSlider = slider;
            sliderX = panel.x;
            updateSlider(x);
        } else {
            setting.cycle();
            settingAnimation(setting).pulse = 1;
            settingAnimation(setting).pulseMotion.reset(1);
        }
    }

    private void startEditingSlider(IntSetting slider, float clickX) {
        configPanel.unfocus();
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
        float target = Math.clamp((x - sliderX - EDGE) / (PANEL_WIDTH - EDGE * 2), 0, 1);
        draggedSlider.setFraction(target);
        settingAnimation((Setting<?>) draggedSlider).sliderTarget = target;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (closing) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        float x = guiMouseX(event.x());
        float y = guiMouseY(event.y());
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
        float maxX = Math.max(SCREEN_MARGIN, screenWidth() - PANEL_WIDTH - SCREEN_MARGIN);
        float minY = HEADER_HEIGHT + SCREEN_MARGIN;
        float maxY = Math.max(minY, screenHeight() - SCREEN_MARGIN - ROW_HEIGHT);
        panel.targetX = Math.clamp(panel.targetX, SCREEN_MARGIN, maxX);
        panel.targetY = Math.clamp(panel.targetY, minY, maxY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_MIDDLE) describing = false;
        if (closing) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (draggedSlider != null) updateSlider(guiMouseX(event.x()));
        boolean handled = draggedPanel != null || draggedSlider != null || draggedColor != null;
        draggedPanel = null;
        draggedSlider = null;
        draggedColor = null;
        colorPanel = null;
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (closing) return true;
        boolean control = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
        if (control || draggedPanel != null) {
            if (vertical != 0) {
                float steps = Math.round(targetResizeScale / RESIZE_STEP) + Math.signum((float) vertical);
                targetResizeScale = Math.clamp(steps * RESIZE_STEP, 0.5f, 2.5f);
            }
            return true;
        }
        float x = guiMouseX(mouseX);
        float y = guiMouseY(mouseY);
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            List<Row> rows = rows(panel);
            if (!inside(x, y, panel.x, panel.y, PANEL_WIDTH, visibleBodyHeight(panel, rows))) continue;
            if (!panel.open) return true;
            if (panel.category == Category.CONFIG) {
                configPanel.mouseScrolled(x, y, vertical);
            } else {
                panel.targetScroll = Math.clamp(panel.targetScroll - (float) vertical * ROW_HEIGHT * 2, 0, maxScroll(panel, rows));
            }
            return true;
        }
        return false;
    }

    private float snapToPixel(float value) {
        float pixels = (float) minecraft.getWindow().getGuiScale() * scale();
        return Math.round(value * pixels) / pixels;
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
        if (open) return;
        deltaTime = clock.nextDelta();
        updateAnimations();
    }

    private void updateAnimations() {
        for (Module module : manager.modules()) {
            ModuleAnimation animation = moduleAnimation(module);
            if (!open) animation.hover = animate(animation.hoverMotion, 0, 18);
            animation.open = Math.clamp(animation.openMotion.update(expanded.contains(module) ? 1 : 0, 28, 1, deltaTime), 0, 1);
            animation.enabled = animation.enabledMotion.update(module.isEnabled() ? 1 : 0, 20, 1, deltaTime);
            for (Setting<?> setting : module.settings()) {
                updateSettingAnimation(setting);
            }
        }
        for (Panel panel : panels) {
            panel.x = snapToPixel(panel.targetX);
            panel.y = snapToPixel(panel.targetY);
            panel.amount = animateLayout(panel.amountMotion, panel.open ? 1 : 0, 20);
            float maximum = maxScroll(panel, rows(panel));
            panel.targetScroll = Math.clamp(panel.targetScroll, 0, maximum);
            float scroll = Math.clamp(animate(panel.scrollMotion, panel.targetScroll, 20), 0, maximum);
            if (scroll != panel.scrollMotion.value()) panel.scrollMotion.reset(scroll);
            panel.scroll = snapToPixel(scroll);
        }
    }

    private void updateSettingAnimation(Setting<?> setting) {
        SettingAnimation state = settingAnimation(setting);
        state.visibility = animate(state.visibilityMotion, setting.isVisible() ? 1 : 0, 28);
        if (!setting.isVisible()) {
            expandedModes.remove(setting);
            expandedColors.remove(setting);
            if (binding == setting) binding = null;
            if (draggedSlider == setting) draggedSlider = null;
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
                (setting instanceof ChoiceSetting && expandedModes.contains(setting)
                        || setting instanceof ColorSetting color && expandedColors.contains(color)) ? 1 : 0, 20);
        if (state.dropdown == 0) state.modeHover.clear();
        state.enabled = state.enabledMotion.update(setting instanceof BooleanSetting toggle && toggle.get() ? 1 : 0,
                20, 1, deltaTime);
        if (setting instanceof SliderSetting slider) {
            float target = draggedSlider == slider ? state.sliderTarget : slider.fraction();
            state.fraction = state.fractionMotion.update(target, draggedSlider == slider ? 32 : 26, deltaTime);
        }
        if (setting instanceof ColorSetting color) {
            state.hue = animate(state.hueMotion, color.hue(), 24);
            state.saturation = animate(state.saturationMotion, color.saturation(), 24);
            state.brightness = animate(state.brightnessMotion, color.brightness(), 24);
        }
        state.pulse = animate(state.pulseMotion, 0, 12);
    }

    private Panel panelAt(float x, float y) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (inside(x, y, panel.x, panel.y - HEADER_HEIGHT, PANEL_WIDTH, HEADER_HEIGHT + visibleBodyHeight(panel, rows(panel)))) {
                return panel;
            }
        }
        return null;
    }

    boolean contains(double mouseX, double mouseY) {
        return panelAt(guiMouseX(mouseX), guiMouseY(mouseY)) != null;
    }

    public void cleanViewBackground(Screen screen) {
        cleanViewBackground = screen;
    }

    public void returnTo(Screen screen) {
        returnTo = screen;
    }

    private void releaseDrags() {
        draggedPanel = null;
        draggedSlider = null;
        draggedColor = null;
        colorPanel = null;
        binding = null;
    }

    @Override
    public void onClose() {
        if (!open || closing) return;
        closing = true;
        releaseDrags();
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
        configPanel.unfocus();
        open = false;
        closing = false;
        windowPop.reset(0);
        releaseDrags();
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
                || configPanel != null && configPanel.focused();
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
        if (configPanel.focused()) {
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
        int typed = event.codepoint();
        if (editingSlider != null) {
            if (!Character.isDigit(typed) && typed != '.' && typed != '-' && typed != '+') return true;
            sliderEditedAt = System.nanoTime();
            return sliderInput.charTyped(event);
        }
        if (configPanel.focused()) return configPanel.charTyped(event);
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
