package cc.swelink.systems.modules.render;

import cc.swelink.Paradox;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.BlurRenderer;
import cc.swelink.renderer.HudBackdrop;
import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.renderer.SdfPipeline;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.client.ClickGuiModule;
import cc.swelink.utils.hud.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModuleListModule extends Module implements PlacedHudElement {
    private static final Set<String> HIDDEN_MODES = Set.of("Rotation", "Movefix mode");
    private static final float FONT_SIZE = 13;
    private static final float BOX_HEIGHT = 17;
    private static final float GAP = 0;
    private static final float ROW_HEIGHT = BOX_HEIGHT + GAP;
    private static final float PADDING = 3;
    private static final float RADIUS = 0;
    private static final float BAR_WIDTH = 2;
    private static final float BAR_SLICE = 3;
    private static final float BORDER_WIDTH = 1;
    private static final float SLIDE_TIME = 0.16f;
    private static final float RELEASE = 0.3f;
    private static final float MAX_BRIDGE = 3;
    private static final Set<String> EMPTY_CHOICES = Set.of("off", "none", "disabled");
    private final BooleanSetting modes = addSetting(new BooleanSetting("Show mode", true));
    private final BooleanSetting background = addSetting(new BooleanSetting("Background", true));
    private final BooleanSetting shadows = addSetting(new BooleanSetting("Shadows", true));
    private final BooleanSetting accentBar = addSetting(new BooleanSetting("Accent bar", true));
    private final BooleanSetting border = addSetting(new BooleanSetting("Border", false));
    private final HudPlacement placement = new HudPlacement(1, 0, false);
    private final FrameClock clock = new FrameClock();
    private final HudPopAnimation pop = new HudPopAnimation();
    private static final String[] PLACEHOLDERS = {"Lorem", "Ipsum"};
    private final Map<Object, Entry> entries = new IdentityHashMap<>();

    public ModuleListModule() {
        super("ModuleList", "Lists enabled modules", Category.RENDER);
        shadows.visibleWhen(background::get);
    }

    @Override
    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if (client.player == null || client.level == null || client.gui.hud.isHidden()
                || !isEnabled() && !pop.exiting()) {
            clear();
            return;
        }
        if (!HudPlacement.canDrag(client) || !isEnabled()) stopDragging();
        boolean dragging = placement.isDragging();
        float delta = clock.nextDelta();
        entries.keySet().removeIf(key -> key instanceof Module module && !listed(module));
        boolean preview = false;
        if (isEnabled()) {
            boolean settled = entries.entrySet().stream().anyMatch(item -> item.getKey() instanceof Module
                    && !item.getValue().leaving && item.getValue().space() > 0.5f);
            for (var module : Paradox.MODULES.modules()) {
                if (module.isEnabled() && listed(module)) {
                    entries.computeIfAbsent(module, ignored -> {
                        var created = entry();
                        if (!settled) created.space.reset(1);
                        return created;
                    });
                }
            }
            preview = HudPlacement.canDrag(client) && Paradox.MODULES.modules().stream()
                    .noneMatch(module -> module.isEnabled() && listed(module));
            if (!preview && Paradox.MODULES.modules().stream().anyMatch(module -> module.isEnabled() && listed(module))) {
                entries.keySet().removeIf(key -> key instanceof String);
            }
            if (preview) {
                for (var placeholder : PLACEHOLDERS) entries.computeIfAbsent(placeholder, ignored -> entry());
            }
        }
        for (var item : entries.entrySet()) {
            float target = shown(item.getKey(), preview) ? 1 : 0;
            item.getValue().leaving = target == 0;
            if (dragging) item.getValue().visibility.reset(target);
            else linear(item.getValue().visibility, target, 1 / SLIDE_TIME, delta);
        }
        boolean showing = preview;
        entries.entrySet().removeIf(item -> !shown(item.getKey(), showing) && item.getValue().visibility.value() == 0);
        if (entries.isEmpty()) {
            clear();
            return;
        }
        var pose = graphics.pose();
        pose.pushMatrix();
        float scale = placement.renderScale(client);
        pose.scale(scale);
        try (var renderer = new Renderer2D(graphics, TextRenderer.Font.INTER_MEDIUM)) {
            boolean left = placement.alignLeft();
            var rows = new ArrayList<Row>();
            float width = 0;
            float height = 0;
            for (var item : entries.entrySet()) {
                String name = item.getKey() instanceof Module module ? module.name() : (String) item.getKey();
                String suffix = item.getKey() instanceof Module module ? suffix(module, left) : "";
                float nameWidth = renderer.text.measure(name, FONT_SIZE).width();
                float suffixWidth = renderer.text.measure(suffix, FONT_SIZE).width();
                float textWidth = nameWidth + suffixWidth;
                float rowWidth = textWidth + PADDING * 2 + barWidth();
                width = Math.max(width, rowWidth);
                var spacing = item.getValue();
                float spaceTarget = spacing.leaving ? 0 : 1;
                if (dragging) spacing.space.reset(spaceTarget);
                else linear(spacing.space, spaceTarget, 1 / SLIDE_TIME, delta);
                height += ROW_HEIGHT * spacing.space();
                rows.add(new Row(name, suffix, nameWidth, suffixWidth, rowWidth, item.getValue()));
            }
            height = Math.max(BOX_HEIGHT, height - GAP);
            Comparator<Row> order = Comparator.comparingDouble(Row::width).reversed().thenComparing(Row::name);
            rows.sort(placement.lowerHalf() ? order.reversed() : order);
            placement.updateAnchored(client, width, height, BOX_HEIGHT);
            pop.apply(renderer, placement.x(), placement.y(), width, height);
            left = placement.alignLeft();
            float originX = placement.x();
            float originY = placement.y();
            float textOffsetX = PADDING + (left ? barWidth() : 0);
            float textOffsetY = (BOX_HEIGHT - renderer.text.lineHeight(FONT_SIZE)) / 2;
            float offset = 0;
            var visibleRows = new ArrayList<VisibleRow>();
            var releasedRows = new ArrayList<VisibleRow>();
            for (var row : rows) {
                float amount = row.entry().visibility.value();
                float restingX = left ? originX : originX + width - row.width();
                float slide = (1 - amount) * row.width();
                float x = restingX + (left ? -slide : slide);
                var entry = row.entry();
                float targetY = originY + offset;
                entry.y.reset(targetY);
                entry.placed = true;
                boolean occupies = entry.occupies();
                float rowY = entry.y.value();
                float top = rowY;
                float bottom = rowY + BOX_HEIGHT;
                float restingLeft = restingX;
                float restingRight = restingX + row.width();
                float rowLeft = x;
                float rowRight = rowLeft + restingRight - restingLeft;
                var visible = new VisibleRow(row, rowLeft, top, rowRight, bottom, restingLeft, restingRight, Math.clamp(amount, 0, 1));
                if (occupies) visibleRows.add(visible);
                else releasedRows.add(visible);
                offset += ROW_HEIGHT * entry.space();
            }
            int count = visibleRows.size();
            int total = count + releasedRows.size();
            int touched = placement.touchedEdges();
            float outerLeft = Float.MAX_VALUE;
            float outerRight = -Float.MAX_VALUE;
            for (var visible : visibleRows) {
                outerLeft = Math.min(outerLeft, visible.restingLeft());
                outerRight = Math.max(outerRight, visible.restingRight());
            }
            var slots = new ArrayList<RowShapes.Slot>();
            for (var visible : visibleRows) {
                slots.add(new RowShapes.Slot(visible.top(), visible.bottom(), visible.shownLeft(), visible.shownRight(),
                        Math.clamp((visible.amount() - RELEASE) / (1 - RELEASE), 0, 1),
                        (touched & BlurRenderer.LEFT) != 0 && Math.abs(visible.restingLeft() - outerLeft) < 0.5f,
                        (touched & BlurRenderer.RIGHT) != 0 && Math.abs(visible.restingRight() - outerRight) < 0.5f));
            }
            boolean atTop = (touched & BlurRenderer.TOP) != 0;
            boolean atBottom = (touched & BlurRenderer.BOTTOM) != 0;
            float screenTop = atTop ? originY : Float.NaN;
            float screenBottom = atBottom ? originY + height : Float.NaN;
            float radius = SdfPipeline.corner(RADIUS);
            var corners = new float[total][];
            var fillets = new float[total][];
            var vertical = new int[total];
            var shadowShapes = new ArrayList<HudBackdrop.Background>();
            for (int i = 0; i < total; i++) {
                corners[i] = new float[] {radius, radius, radius, radius};
                fillets[i] = new float[4];
            }
            for (int i = 0; i < count; i++) {
                var visible = visibleRows.get(i);
                var slot = slots.get(i);
                float[] shape = RowShapes.corners(slots, i, radius, screenTop, screenBottom);
                for (int corner = 0; corner < 4; corner++) {
                    boolean docked = corner == 0 || corner == 3 ? slot.dockLeft() : slot.dockRight();
                    corners[i][corner] = Math.max(shape[corner], 0);
                    fillets[i][corner] = Math.max(-shape[corner], 0);
                    if (docked && shape[corner] < 0) vertical[i] |= 1 << corner;
                }
                boolean dockTop = atTop && Math.abs(visible.top() - originY) < 0.5f;
                boolean dockBottom = atBottom && Math.abs(visible.bottom() - originY - height) < 0.5f;
                int rowEdges = (slot.dockLeft() ? BlurRenderer.LEFT : 0) | (slot.dockRight() ? BlurRenderer.RIGHT : 0)
                        | (dockTop ? BlurRenderer.TOP : 0) | (dockBottom ? BlurRenderer.BOTTOM : 0);
                shadowShapes.add(shadowShape(visible, rowEdges, corners[i]));
            }
            for (var released : releasedRows) {
                int index = visibleRows.size();
                shadowShapes.add(shadowShape(released, 0, corners[index]));
                visibleRows.add(released);
            }
            if (background.get() && shadows.get()) {
                for (int i = 0; i < shadowShapes.size(); i++) {
                    renderer.opacity(pop.value() * visibleRows.get(i).amount());
                    HudBackdrop.draw(renderer, shadowShapes.get(i), List.of());
                }
                renderer.opacity(pop.value());
            }
            int primary = ThemeColors.primary();
            int secondary = ThemeColors.secondary();
            float phase = ThemeColors.wavePhase();
            float seam = 1f / Math.max(Math.abs(pose.m11()) * client.getWindow().getGuiScale(), 0.0001f);
            var drawOrder = new ArrayList<Integer>(total);
            for (int i = count; i < total; i++) drawOrder.add(i);
            for (int i = 0; i < count; i++) if (visibleRows.get(i).amount() < 0.999f) drawOrder.add(i);
            for (int i = 0; i < count; i++) if (visibleRows.get(i).amount() >= 0.999f) drawOrder.add(i);
            for (int i : drawOrder) {
                var visible = visibleRows.get(i);
                float overlap = i > 0 && i < count ? overlap(visibleRows.get(i - 1), visible, seam) : 0;
                var row = visible.row();
                float clipLeft = left ? visible.restingLeft() : visible.restingLeft() - radius;
                float clipRight = left ? visible.restingRight() + radius : visible.restingRight();
                try (var fade = renderer.pushOpacity(FrameClock.smoothStep(Math.clamp(visible.amount() * 1.6f, 0, 1)))) {
                    if (background.get()) {
                        renderer.blur.frostedGlassShape(renderer, visible.shownLeft(), visible.top() - overlap,
                                visible.shownRight() - visible.shownLeft(), visible.bottom() - visible.top() + overlap,
                                ThemeColors.hudPanel(), corners[i], fillets[i], vertical[i]);
                    }
                }
                try (var clip = visible.amount() < 1 ? renderer.clips.push(clipLeft, visible.top() - radius,
                        clipRight - clipLeft, BOX_HEIGHT + radius * 2) : null;
                     var fade = renderer.pushOpacity(FrameClock.smoothStep(Math.clamp(visible.amount() * 1.6f, 0, 1)))) {
                    if (accentBar.get()) {
                        float barX = left ? visible.left() : visible.left() + row.width() - BAR_WIDTH;
                        waveLine(renderer, barX, visible.top() - overlap, BAR_WIDTH, visible.bottom(), originY, primary, secondary, phase);
                    }
                    float textY = visible.top() + textOffsetY;
                    float textX = visible.left() + textOffsetX;
                    float nameX = left ? textX + row.suffixWidth() : textX;
                    float suffixX = left ? textX : textX + row.nameWidth();
                    renderer.text.drawColored(row.name(), nameX, textY, FONT_SIZE,
                            (glyphX, glyphY) -> ThemeColors.wave(primary, secondary, glyphY - originY, phase));
                    renderer.text.draw(row.suffix(), suffixX, textY, FONT_SIZE, 0xFFB8B7BE);
                }
            }
            if (border.get()) drawBorder(renderer, visibleRows.subList(0, count), originY, primary, secondary, phase);
        } finally {
            pose.popMatrix();
        }
    }

    private float barWidth() {
        return accentBar.get() ? BAR_WIDTH : 0;
    }

    private static void drawBorder(Renderer2D renderer, List<VisibleRow> rows, float originY,
                                   int primary, int secondary, float phase) {
        var present = new ArrayList<VisibleRow>();
        for (var row : rows) if (row.present()) present.add(row);
        present.sort(Comparator.comparingDouble(VisibleRow::top));
        for (int i = 0; i < present.size(); i++) {
            var row = present.get(i);
            float rowLeft = row.shownLeft();
            float rowRight = row.shownRight();
            waveLine(renderer, rowLeft, row.top(), BORDER_WIDTH, row.bottom(), originY, primary, secondary, phase);
            waveLine(renderer, rowRight - BORDER_WIDTH, row.top(), BORDER_WIDTH, row.bottom(), originY, primary, secondary, phase);
            var above = i > 0 ? present.get(i - 1) : null;
            var below = i + 1 < present.size() ? present.get(i + 1) : null;
            edge(renderer, row, above, row.top(), originY, primary, secondary, phase);
            edge(renderer, row, below, row.bottom() - BORDER_WIDTH, originY, primary, secondary, phase);
        }
    }

    private static void edge(Renderer2D renderer, VisibleRow row, VisibleRow neighbor, float y, float originY,
                             int primary, int secondary, float phase) {
        float rowLeft = row.shownLeft();
        float rowRight = row.shownRight();
        int color = ThemeColors.wave(primary, secondary, y + BORDER_WIDTH / 2 - originY, phase);
        boolean touching = neighbor != null && Math.abs(neighbor.top() - row.bottom()) < 0.5f
                || neighbor != null && Math.abs(row.top() - neighbor.bottom()) < 0.5f;
        if (!touching) {
            renderer.fill(rowLeft, y, rowRight - rowLeft, BORDER_WIDTH, color);
            return;
        }
        float otherLeft = neighbor.shownLeft();
        float otherRight = neighbor.shownRight();
        if (otherLeft > rowLeft) {
            float end = Math.min(otherLeft + BORDER_WIDTH, rowRight);
            renderer.fill(rowLeft, y, end - rowLeft, BORDER_WIDTH, color);
        }
        if (otherRight < rowRight) {
            float start = Math.max(otherRight - BORDER_WIDTH, rowLeft);
            renderer.fill(start, y, rowRight - start, BORDER_WIDTH, color);
        }
    }

    private static void waveLine(Renderer2D renderer, float x, float top, float width, float bottom, float originY,
                                 int primary, int secondary, float phase) {
        for (float y = top; y < bottom; y += BAR_SLICE) {
            float sliceBottom = Math.min(y + BAR_SLICE, bottom);
            int topColor = ThemeColors.wave(primary, secondary, y - originY, phase);
            int bottomColor = ThemeColors.wave(primary, secondary, sliceBottom - originY, phase);
            renderer.gradient(x, y, width, sliceBottom - y, topColor, bottomColor, bottomColor, topColor);
        }
    }

    private static HudBackdrop.Background shadowShape(VisibleRow row, int edges, float[] corners) {
        float width = row.shownRight() - row.shownLeft();
        float[] fitted = corners.clone();
        for (int corner = 0; corner < fitted.length; corner++) fitted[corner] = Math.min(fitted[corner], width / 2);
        return HudBackdrop.docked(row.shownLeft(), row.top(), width, row.bottom() - row.top(), RADIUS, edges)
                .withCorners(fitted);
    }

    private static float overlap(VisibleRow upper, VisibleRow lower, float seam) {
        if (!upper.present() || !lower.present()) return 0;
        float gap = lower.top() - upper.bottom();
        return gap > -0.5f && gap < MAX_BRIDGE ? Math.max(gap, 0) + seam : 0;
    }

    private static void linear(SpringAnimation animation, float target, float rate, float delta) {
        float value = animation.value();
        float step = rate * delta;
        animation.reset(Math.abs(target - value) <= step ? target : value + Math.signum(target - value) * step);
    }

    private Entry entry() {
        var entry = new Entry();
        if (pop.opening()) {
            entry.visibility.reset(1);
            entry.space.reset(1);
        }
        return entry;
    }

    private static boolean listed(Module module) {
        return module.category() != Category.RENDER && !(module instanceof ClickGuiModule);
    }

    private static boolean shown(Object key, boolean preview) {
        return key instanceof Module module ? module.isEnabled() : preview;
    }

    private String suffix(Module module, boolean left) {
        String mode = mode(module);
        if (mode.isEmpty()) return "";
        return left ? mode + " " : " " + mode;
    }

    private String mode(Module module) {
        if (!modes.get()) return "";
        ModeSetting primary = null;
        for (var setting : module.settings()) {
            if (!(setting instanceof ModeSetting mode) || !setting.isVisible() || HIDDEN_MODES.contains(setting.name())
                    || EMPTY_CHOICES.contains(mode.selectedChoice().toLowerCase(Locale.ROOT))) continue;
            if (setting.name().equals("Mode")) return mode.selectedChoice();
            if (primary == null && setting.name().toLowerCase(Locale.ROOT).endsWith("mode")) primary = mode;
        }
        return primary == null ? "" : primary.selectedChoice();
    }

    private void clear() {
        entries.clear();
        placement.clearSize();
        clock.reset();
        stopDragging();
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
    protected void onEnable() {
        clear();
        pop.reset();
    }

    @Override
    protected void onDisable() {
        pop.close();
        stopDragging();
    }

    private static final class Entry {
        private final SpringAnimation visibility = new SpringAnimation();
        private final SpringAnimation y = new SpringAnimation();
        private final SpringAnimation space = new SpringAnimation();
        private boolean placed;
        private boolean leaving;

        float space() {
            return Math.clamp(space.value(), 0, 1);
        }

        boolean occupies() {
            return !leaving || visibility.value() > RELEASE;
        }
    }

    private record Row(String name, String suffix, float nameWidth, float suffixWidth, float width, Entry entry) {
    }

    private record VisibleRow(Row row, float left, float top, float right, float bottom,
                              float restingLeft, float restingRight, float amount) {
        float shownLeft() {
            return Math.max(left, restingLeft);
        }

        float shownRight() {
            return Math.min(right, restingRight);
        }

        boolean present() {
            return shownRight() - shownLeft() >= 0.5f;
        }
    }
}
