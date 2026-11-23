package cc.swelink.systems.modules.render;

import cc.swelink.events.EventHandler;
import cc.swelink.events.OverlayRenderEvent;
import cc.swelink.renderer.DigitAnimation;
import cc.swelink.renderer.DistanceScale;
import cc.swelink.renderer.BlurRenderer;
import cc.swelink.renderer.HudBackdrop;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.Targets;
import cc.swelink.utils.hud.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class NametagsModule extends Module {
    public static final float FONT_SIZE = 15;
    private static final float PADDING = 7;
    private static final float HEIGHT = 22;
    private static final float CORNER_RADIUS = 6;
    private static final float ICON_SIZE = 14;
    private static final float ICON_GAP = 5;
    private static final float MAX_DISTANCE_SCALE = 4;
    private static final double TAG_LIFT = 0.5;
    private final BooleanSetting players = addSetting(new BooleanSetting("Players", true));
    private final BooleanSetting items = addSetting(new BooleanSetting("Items", true));
    private final BooleanSetting others = addSetting(new BooleanSetting("Others", false));
    private final BooleanSetting shadows = addSetting(new BooleanSetting("Shadows", true));
    private final IntSetting size = addSetting(new IntSetting("Size", 100, 50, 200) {
        @Override
        protected String formatValue(int value) {
            return value + "%";
        }
    });

    public NametagsModule() {
        super("Nametags", "Better name tags", Category.RENDER);
    }

    public boolean replaces(EntityType<?> type) {
        return isEnabled();
    }

    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!isEnabled() || client.player == null || client.level == null || client.gui.hud.isHidden()) return;
        var camera = client.gameRenderer.mainCamera();
        var matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
        float tick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float scale = HudPlacement.scale(client);
        float viewportWidth = graphics.guiWidth() / scale;
        float viewportHeight = graphics.guiHeight() / scale;
        var entities = new ArrayList<Entity>();
        for (var entity : client.level.entitiesForRendering()) if (shown(client, entity)) entities.add(entity);
        entities.sort(Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(client.player)).reversed());
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale);
        try (var renderer = new Renderer2D(graphics)) {
            for (var entity : entities) {
                var attachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(tick));
                double lift = (attachment == null ? entity.getBbHeight() : attachment.y) + TAG_LIFT;
                var anchor = entity.getPosition(tick).add(0, lift, 0).subtract(camera.position());
                var projected = matrix.transform(new Vector4f((float) anchor.x, (float) anchor.y, (float) anchor.z, 1));
                if (!projected.isFinite() || projected.w <= 0.001f) continue;
                float centerX = (projected.x / projected.w + 1) * viewportWidth / 2;
                float bottomY = (1 - projected.y / projected.w) * viewportHeight / 2;
                if (centerX < 0 || centerX > viewportWidth || bottomY < 0 || bottomY > viewportHeight) continue;
                drawTag(renderer, label(entity), centerX, bottomY, DistanceScale.depth(projected.w, matrix));
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private boolean shown(Minecraft client, Entity entity) {
        if (entity == client.player || entity.isRemoved() || entity.isInvisibleTo(client.player)) return false;
        if (entity instanceof Player player) {
            return players.get() && player.isAlive() && !player.isSpectator() && !Targets.bot(player);
        }
        if (entity instanceof ItemEntity) return items.get();
        if (entity.shouldShowName()) return true;
        return others.get() && entity instanceof LivingEntity living && living.isAlive();
    }

    private record Segment(String text, int color) {
    }

    private static List<Segment> label(Entity entity) {
        if (entity instanceof ItemEntity item) {
            var stack = item.getItem();
            var name = stack.getHoverName();
            return segments(stack.getCount() > 1 ? Component.empty().append(name).append(" x" + stack.getCount()) : name);
        }
        return segments(entity.getDisplayName());
    }

    private static List<Segment> segments(Component component) {
        var segments = new ArrayList<Segment>();
        component.visit((style, string) -> {
            int color = style.getColor() == null ? 0xFFFFFF : style.getColor().getValue();
            var text = new StringBuilder();
            for (int i = 0; i < string.length(); i++) {
                char c = string.charAt(i);
                if (c == '\u00A7' && i + 1 < string.length()) {
                    var format = ChatFormatting.getByCode(string.charAt(++i));
                    if (format == null) continue;
                    var legacy = TextColor.fromLegacyFormat(format);
                    if (legacy != null || format == ChatFormatting.RESET) {
                        add(segments, text.toString(), color);
                        text.setLength(0);
                        color = legacy == null ? 0xFFFFFF : legacy.getValue();
                    }
                    continue;
                }
                text.append(c);
            }
            add(segments, text.toString(), color);
            return Optional.empty();
        }, Style.EMPTY);
        return segments;
    }

    private static void add(List<Segment> segments, String text, int color) {
        if (text.isEmpty()) return;
        int argb = 0xFF000000 | color;
        if (!segments.isEmpty() && segments.getLast().color() == argb) {
            segments.set(segments.size() - 1, new Segment(segments.getLast().text() + text, argb));
        } else {
            segments.add(new Segment(text, argb));
        }
    }

    private void drawTag(Renderer2D renderer, List<Segment> segments, float centerX, float bottomY, float depth) {
        if (segments.isEmpty()) return;
        var pose = renderer.graphics().pose();
        pose.pushMatrix();
        try {
            pose.translate(centerX, bottomY);
            pose.scale(DistanceScale.atDepth(depth, MAX_DISTANCE_SCALE) * size.get() / 100f);
            float[] widths = new float[segments.size()];
            float width = 0;
            for (int i = 0; i < widths.length; i++) {
                widths[i] = renderer.text.measure(segments.get(i).text(), FONT_SIZE).width();
                width += widths[i];
            }
            float x = -width / 2;
            float top = -renderer.text.lineHeight(FONT_SIZE);
            for (int i = 0; i < widths.length; i++) {
                renderer.text.draw(segments.get(i).text(), x, top, FONT_SIZE, segments.get(i).color(), true);
                x += widths[i];
            }
        } finally {
            pose.popMatrix();
        }
    }

    public void drawAt(GuiGraphicsExtractor graphics, DigitAnimation animation, String text, ItemStack icon,
                       float centerX, float bottomY, float depth, float delta) {
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(centerX, bottomY);
            pose.scale(DistanceScale.atDepth(depth, MAX_DISTANCE_SCALE));
            float left;
            try (var renderer = new Renderer2D(graphics)) {
                animation.update(renderer, text, delta);
                float contentWidth = ICON_SIZE + ICON_GAP + animation.width();
                left = -contentWidth / 2 - PADDING;
                drawPanel(renderer, left, -HEIGHT, contentWidth + PADDING * 2);
                animation.draw(renderer, left + PADDING + ICON_SIZE + ICON_GAP,
                        -HEIGHT + (HEIGHT - renderer.text.lineHeight(FONT_SIZE)) / 2, (x, y) -> ThemeColors.text());
            }
            graphics.nextStratum();
            pose.pushMatrix();
            pose.translate(left + PADDING, -HEIGHT + (HEIGHT - ICON_SIZE) / 2);
            pose.scale(ICON_SIZE / 16f);
            graphics.fakeItem(icon, 0, 0);
            pose.popMatrix();
        } finally {
            pose.popMatrix();
        }
    }

    private void drawPanel(Renderer2D renderer, float left, float top, float width) {
        BlurRenderer.endGroup(renderer);
        if (shadows.get()) {
            HudBackdrop.draw(renderer, List.of(new HudBackdrop.Background(left, top, width, HEIGHT, CORNER_RADIUS)));
        }
        renderer.blur.hudGlass(renderer, left, top, width, HEIGHT, CORNER_RADIUS, ThemeColors.hudPanel());
    }

    @EventHandler(priority = 30)
    private void onOverlayRender(OverlayRenderEvent event) {
        render(event.client(), event.graphics());
    }
}
