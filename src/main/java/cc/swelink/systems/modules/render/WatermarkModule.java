package cc.swelink.systems.modules.render;

import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.Glass;
import cc.swelink.renderer.DigitAnimation;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.Paradox;
import cc.swelink.renderer.HudBackdrop;
import cc.swelink.renderer.HudRevealAnimation;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.hud.HudPlacement;
import cc.swelink.utils.player.LatencyTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class WatermarkModule extends Module implements PlacedHudElement {
    private final LatencyTracker latencyTracker = new LatencyTracker();
    private static final float HEIGHT = 30;
    private static final float FONT_SIZE = 15;
    private static final float CORNER_RADIUS = 15;
    private static final float PADDING = 12;
    private static final float ICON_SIZE = 14;
    private static final float ICON_GAP = 6;
    private static final float BOX_GAP = 5;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final BooleanSetting fps = addSetting(new BooleanSetting("FPS", true));
    private final BooleanSetting latency = addSetting(new BooleanSetting("Latency", true));
    private final BooleanSetting time = addSetting(new BooleanSetting("Time", true));
    private final BooleanSetting shadows = addSetting(new BooleanSetting("Shadows", true));
    private final FrameClock clock = new FrameClock();
    private final HudRevealAnimation fpsAnimation = new HudRevealAnimation();
    private final HudRevealAnimation latencyAnimation = new HudRevealAnimation();
    private final HudRevealAnimation timeAnimation = new HudRevealAnimation();
    private float fpsWidth = -1;
    private float latencyWidth = -1;
    private String fpsText = "0 fps";
    private String latencyText = "-- ms";
    private final DigitAnimation fpsDigits = new DigitAnimation(FONT_SIZE);
    private final DigitAnimation latencyDigits = new DigitAnimation(FONT_SIZE);
    private final DigitAnimation timeDigits = new DigitAnimation(FONT_SIZE);
    private String timeText = "00:00:00";
    private long nextInfoUpdate;
    private final HudPlacement placement = new HudPlacement(0, 0, false).withCenterSnap();
    private final HudPopAnimation enablePop = new HudPopAnimation();
    private float width;
    private final List<String> badgeOrder = new ArrayList<>(List.of(
            "fps", "latency", "clock"));
    private final Map<String, BadgeMotion> badgeMotions = new HashMap<>();
    private final Map<String, Bounds> badgeBounds = new HashMap<>();
    private final Map<String, Bounds> badgeTargets = new HashMap<>();
    private String draggedBadge;
    private float badgeGrabX;

    public WatermarkModule() {
        super("Watermark", "Shows FPS, ping and time", Category.RENDER);
    }

    @Override
    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!isEnabled() && !enablePop.exiting()) {
            width = 0;
            placement.clearSize();
        }
        if ((!isEnabled() && !enablePop.exiting()) || client.player == null || client.level == null || client.gui.hud.isHidden()) {
            stopDragging();
            clock.reset();
            return;
        }
        if (!HudPlacement.canDrag(client)) {
            stopDragging();
        }
        float delta = clock.nextDelta();
        fpsAnimation.update(fps.get(), delta);
        latencyAnimation.update(latency.get(), delta);
        timeAnimation.update(time.get(), delta);
        updateInfo(client);

        float scale = placement.renderScale(client);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale);
        try (var renderer = new Renderer2D(graphics)) {
            fpsDigits.update(renderer, fpsText, delta);
            latencyDigits.update(renderer, latencyText, delta);
            timeDigits.update(renderer, timeText, delta);
            fpsWidth = resize(fpsWidth, fpsDigits.width(), delta);
            latencyWidth = resize(latencyWidth, latencyDigits.width(), delta);
            float timeWidth = timeDigits.width();
            if (draggedBadge != null) {
                var window = client.getWindow();
                reorderBadge((float) client.mouseHandler.getScaledXPos(window) / scale);
            }
            var available = Map.of(
                    "fps", new Segment(fpsText, fpsWidth, "fps", fpsAnimation),
                    "clock", new Segment(timeText, timeWidth, "clock", timeAnimation),
                    "latency", new Segment(latencyText, latencyWidth, "latency", latencyAnimation));
            width = 0;
            float preceding = 0;
            for (String id : badgeOrder) {
                Segment segment = available.get(id);
                float amount = segment.animation.space();
                width += (segment.boxWidth() + BOX_GAP * preceding) * amount;
                preceding = Math.max(preceding, amount);
            }
            float height = HEIGHT;
            if (width <= 0) {
                placement.clearSize();
                return;
            }
            placement.updateAnchored(client, width, height, HEIGHT);
            enablePop.apply(renderer, placement.x(), placement.y(), width, height, placement.touchedEdges());
            TextRenderer.ColorFunction colors = (x, y) -> ThemeColors.text();

            var badges = new ArrayList<Badge>();
            badgeBounds.clear();
            badgeTargets.clear();
            float y = placement.y();
            var window = client.getWindow();
            float mouseX = (float) client.mouseHandler.getScaledXPos(window) / scale;
            float x = placement.x();
            preceding = 0;
            for (String id : badgeOrder) {
                Segment segment = available.get(id);
                float amount = segment.animation.space();
                float gap = BOX_GAP * preceding * amount;
                float boxWidth = segment.boxWidth() * amount;
                x += gap;
                badgeTargets.put(id, new Bounds(x, y, boxWidth, HEIGHT));
                float targetX = x - placement.x();
                if (id.equals(draggedBadge)) {
                    targetX = Math.clamp(mouseX - badgeGrabX - placement.x(), 0, Math.max(0, width - boxWidth));
                }
                var motion = badgeMotions.computeIfAbsent(id, ignored -> new BadgeMotion());
                motion.update(targetX, delta, draggedBadge != null);
                float badgeX = placement.x() + Math.clamp(motion.x, 0, Math.max(0, width - boxWidth));
                badgeBounds.put(id, new Bounds(badgeX, y, boxWidth, HEIGHT));
                badges.add(new Badge(segment, new HudBackdrop.Background(badgeX, y, boxWidth, HEIGHT, CORNER_RADIUS)));
                x += boxWidth;
                preceding = Math.max(preceding, amount);
            }
            int edges = placement.touchedEdges();
            if (shadows.get()) HudBackdrop.draw(renderer, List.of(HudBackdrop.docked(placement.x(), placement.y(), width, height,
                    CORNER_RADIUS, edges)));
            renderer.blur.frostedGlass(renderer, Glass.HUD, placement.x(), placement.y(), width, height, CORNER_RADIUS,
                    ThemeColors.hudPanel(), edges);
            preceding = 0;
            for (String id : badgeOrder) {
                Segment segment = available.get(id);
                float amount = segment.animation.space();
                Bounds slot = badgeTargets.get(id);
                float separatorOpacity = Math.min(preceding, segment.animation.slide());
                if (separatorOpacity > 0 && amount > 0) {
                    float separatorX = slot.x() - BOX_GAP * preceding * amount / 2;
                    renderer.opacity(separatorOpacity * enablePop.value());
                    renderer.sdf.roundedRect(separatorX - 0.5f, y + 7, 1, HEIGHT - 14, 0.5f, 0xFF38383D);
                    renderer.opacity(enablePop.value());
                }
                preceding = Math.max(preceding, amount);
            }
            if (draggedBadge != null) badges.sort(Comparator.comparing(badge -> badge.segment.icon.equals(draggedBadge)));
            try (var statsClip = renderer.clips.push(placement.x(), placement.y(), width, HEIGHT)) {
                for (Badge badge : badges) {
                    Segment segment = badge.segment;
                    var box = badge.box;
                    float amount = segment.animation.slide();
                    if (box.width() <= 0 || amount <= 0) continue;
                    float textY = box.y() + (HEIGHT - renderer.text.lineHeight(FONT_SIZE)) / 2;
                    var pose = graphics.pose();
                    pose.pushMatrix();
                    try {
                        renderer.opacity(amount * enablePop.value());
                        if (segment.icon.equals(draggedBadge)) {
                            renderer.sdf.roundedRect(box.x() + 2, box.y() + 3, Math.max(0, box.width() - 4), HEIGHT - 6,
                                    CORNER_RADIUS - 3, 0xFF1C1C20);
                        }
                        try (var clip = renderer.clips.push(box.x(), box.y(), box.width(), HEIGHT)) {
                            float iconSize = segment.iconSize();
                            float iconX = box.x() + PADDING;
                            float iconY = box.y() + (HEIGHT - iconSize) / 2;
                            renderer.textures.icon(Paradox.id("icons/hud/" + segment.icon + ".png"),
                                    iconX, iconY, iconSize, AccentWave.text());
                            float textX = iconX + iconSize + ICON_GAP;
                            if (segment.icon.equals("fps")) {
                                fpsDigits.draw(renderer, textX, textY, colors);
                            } else if (segment.icon.equals("latency")) {
                                latencyDigits.draw(renderer, textX, textY, colors);
                            } else if (segment.icon.equals("clock")) {
                                timeDigits.draw(renderer, textX, textY, colors);
                            } else {
                                renderer.text.drawColored(segment.text, textX, textY, FONT_SIZE, colors);
                            }
                        }
                    } finally {
                        renderer.opacity(enablePop.value());
                        pose.popMatrix();
                    }
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void reorderBadge(float mouseX) {
        if (draggedBadge == null) return;
        Bounds dragged = badgeBounds.get(draggedBadge);
        if (dragged == null) return;
        float centerX = mouseX - badgeGrabX + dragged.width() / 2;
        badgeOrder.remove(draggedBadge);
        int insertion = badgeOrder.size();
        for (int i = 0; i < badgeOrder.size(); i++) {
            Bounds bounds = badgeTargets.get(badgeOrder.get(i));
            if (bounds == null || bounds.width() <= 0) continue;
            float middle = bounds.x() + bounds.width() / 2;
            if (centerX < middle) {
                insertion = i;
                break;
            }
        }
        badgeOrder.add(insertion, draggedBadge);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event) {
        var client = Minecraft.getInstance();
        if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT) return PlacedHudElement.super.mouseClicked(event);
        if (!isEnabled() || client.player == null || client.gui.hud.isHidden() || !HudPlacement.canDrag(client)) return false;
        float scale = placement.renderScale(client);
        float x = (float) event.x() / scale;
        float y = (float) event.y() / scale;
        for (var entry : badgeBounds.entrySet()) {
            if (!entry.getValue().contains(x, y)) continue;
            draggedBadge = entry.getKey();
            badgeGrabX = x - entry.getValue().x();
            placement.stopDragging();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event) {
        if (draggedBadge == null) return PlacedHudElement.super.mouseDragged(event);
        if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT) return false;
        float scale = placement.renderScale(Minecraft.getInstance());
        reorderBadge((float) event.x() / scale);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggedBadge == null) return PlacedHudElement.super.mouseReleased(event);
        if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT) return false;
        float scale = placement.renderScale(Minecraft.getInstance());
        reorderBadge((float) event.x() / scale);
        draggedBadge = null;
        return true;
    }

    @Override
    public void stopDragging() {
        PlacedHudElement.super.stopDragging();
        draggedBadge = null;
    }

    private record Badge(Segment segment, HudBackdrop.Background box) {
    }

    private static final class BadgeMotion {
        float x;
        boolean initialized;

        void update(float targetX, float delta, boolean reordering) {
            if (!initialized || !reordering) {
                x = targetX;
                initialized = true;
            } else {
                x = FrameClock.approach(x, targetX, 45, delta);
            }
        }
    }

    private void updateInfo(Minecraft client) {
        latencyTracker.update(client, isEnabled() && latency.get());
        long now = System.nanoTime();
        if (now < nextInfoUpdate) {
            return;
        }
        fpsText = Math.max(0, client.getFps()) + " fps";
        long ping = latencyTracker.milliseconds(client);
        latencyText = ping < 0 ? "-- ms" : ping + " ms";
        timeText = LocalTime.now().format(TIME_FORMAT);
        nextInfoUpdate = now + 250_000_000L;
    }

    public void onLatencyPong(ClientPacketListener source, long token) {
        latencyTracker.onPong(source, token);
    }

    private record Segment(String text, float textWidth, String icon, HudRevealAnimation animation) {
        float iconSize() {
            return icon.equals("fps") ? ICON_SIZE : 16;
        }

        float boxWidth() {
            return PADDING * 2 + iconSize() + ICON_GAP + textWidth;
        }
    }

    private float resize(float current, float target, float delta) {
        if (current < 0) return target;
        float next = FrameClock.approach(current, target, 45, delta);
        return Math.abs(next - target) < 0.1f ? target : next;
    }

    @Override
    protected void onEnable() {
        latencyTracker.reset();
        enablePop.reset();
        badgeMotions.clear();
        badgeBounds.clear();
        badgeTargets.clear();
        fpsAnimation.reset(fps.get());
        latencyAnimation.reset(latency.get());
        timeAnimation.reset(time.get());
        fpsWidth = -1;
        latencyWidth = -1;
        nextInfoUpdate = 0;
        fpsDigits.reset();
        latencyDigits.reset();
        timeDigits.reset();
        clock.reset();
    }

    @Override
    public float cornerRadius() {
        return CORNER_RADIUS;
    }

    @Override
    public HudPlacement placement() {
        return placement;
    }

    @Override
    protected void onDisable() {
        latencyTracker.reset();
        enablePop.close();
        stopDragging();
    }
}
