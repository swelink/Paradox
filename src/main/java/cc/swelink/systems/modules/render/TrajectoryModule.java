package cc.swelink.systems.modules.render;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.OverlayRenderEvent;
import cc.swelink.mixin.ArrowTrajectoryAccessor;
import cc.swelink.mixin.ProjectileTrajectoryAccessor;
import cc.swelink.renderer.DigitAnimation;
import cc.swelink.renderer.FrameClock;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.hud.HudPlacement;
import cc.swelink.utils.projectile.BlockCollisions;
import cc.swelink.utils.projectile.HeldThrow;
import cc.swelink.utils.projectile.ProjectileTrajectory;
import cc.swelink.utils.projectile.WeaponShot;
import cc.swelink.utils.rotation.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import cc.swelink.renderer.DistanceScale;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrajectoryModule extends Module {
    private static final UUID HELD_TAG = new UUID(0, 0);
    private static final double TAG_GLIDE = 35;
    private static final double PATH_GLIDE = 28;
    private static final int PATH_POINTS = 96;
    private final BooleanSetting predictionTag = addSetting(new BooleanSetting("Prediction nametag", true));
    private List<Prediction> predictions = List.of();
    private final Map<UUID, Cached> cache = new HashMap<>();
    private final Map<UUID, Tag> tags = new HashMap<>();
    private final FrameClock tagClock = new FrameClock();
    private final Map<UUID, SmoothPath> smoothed = new HashMap<>();
    private final FrameClock pathClock = new FrameClock();
    private ClientLevel sourceLevel;

    public TrajectoryModule() {
        super("Trajectories", "Shows where projectiles land", Category.RENDER);
    }

    public record Prediction(Projectile entity, ProjectileTrajectory.Path path, ItemStack icon) {
        public Vec3 origin(float partialTick) {
            return entity == null ? path.points().getFirst() : entity.getPosition(partialTick);
        }

        private boolean alive() {
            return entity == null || entity.isAlive();
        }

        private UUID id() {
            return entity == null ? HELD_TAG : entity.getUUID();
        }
    }

    private static final class Tag {
        private final DigitAnimation digits = new DigitAnimation(NametagsModule.FONT_SIZE);
        private Vec3 position;
        private String text;
        private ItemStack icon;

        private Tag(Vec3 position) {
            this.position = position;
        }

        private void glide(Vec3 target, float delta) {
            position = position.lerp(target, -Math.expm1(-TAG_GLIDE * delta));
        }
    }

    private static final class SmoothPath {
        private final Vec3[] points;
        private Vec3 impact;
        private Vec3 normal;

        private SmoothPath(Vec3[] points) {
            this.points = points;
        }
    }

    private record Cached(Projectile entity, Vec3 position, Vec3 velocity, int tick, double gravity,
                          ProjectileTrajectory.Path path) {
    }

    public List<Prediction> update(Minecraft client, float partialTick) {
        if (!isEnabled() || client.player == null || client.level == null || client.gui.hud.isHidden()) {
            clear();
            return List.of();
        }
        if (sourceLevel != client.level) {
            clear();
            sourceLevel = client.level;
        }
        var current = new ArrayList<Prediction>();
        var active = new HashSet<UUID>();
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Projectile source) || !source.isAlive() || !matches(source)) continue;
            if (source instanceof AbstractArrow arrow
                    && (((ArrowTrajectoryAccessor) arrow).paradox$isInGround() || arrow.isNoPhysics())) continue;
            UUID id = source.getUUID();
            active.add(id);
            Vec3 position = source.position();
            Vec3 velocity = source.getDeltaMovement();
            double gravity = source.isNoGravity() ? 0 : source.getGravity();
            Cached previous = cache.get(id);
            if (previous == null || previous.entity() != source || previous.tick() != source.tickCount
                    || !previous.position().equals(position) || !previous.velocity().equals(velocity)
                    || previous.gravity() != gravity) {
                var path = predict(client.level, source, position, velocity, gravity);
                previous = new Cached(source, position, velocity, source.tickCount, gravity, path);
                cache.put(id, previous);
            }
            current.add(new Prediction(source, previous.path(), icon(source)));
        }
        cache.keySet().retainAll(active);
        var held = held(client, partialTick);
        if (held != null) current.add(held);
        float delta = pathClock.nextDelta();
        var visible = new HashSet<UUID>();
        var smooth = new ArrayList<Prediction>(current.size());
        for (var prediction : current) {
            visible.add(prediction.id());
            smooth.add(new Prediction(prediction.entity(), smooth(prediction.id(), prediction.path(), delta), prediction.icon()));
        }
        smoothed.keySet().retainAll(visible);
        predictions = List.copyOf(smooth);
        return predictions;
    }

    private ProjectileTrajectory.Path smooth(UUID id, ProjectileTrajectory.Path path, float delta) {
        if (path.points().isEmpty()) return path;
        Vec3[] target = resample(path.points());
        var state = smoothed.get(id);
        double blend = -Math.expm1(-PATH_GLIDE * delta);
        if (state == null) {
            state = new SmoothPath(target);
            smoothed.put(id, state);
        } else {
            for (int i = 0; i < PATH_POINTS; i++) state.points[i] = state.points[i].lerp(target[i], blend);
        }
        var impact = path.impact();
        if (impact == null) {
            state.impact = state.normal = null;
        } else if (state.impact == null) {
            state.impact = impact.position();
            state.normal = impact.normal();
        } else {
            state.impact = state.impact.lerp(impact.position(), blend);
            var normal = state.normal.lerp(impact.normal(), blend);
            state.normal = normal.lengthSqr() > 1.0e-6 ? normal.normalize() : impact.normal();
        }
        return new ProjectileTrajectory.Path(List.of(state.points.clone()),
                state.impact == null ? null : new ProjectileTrajectory.Impact(state.impact, state.normal), path.ticks());
    }

    private static Vec3[] resample(List<Vec3> points) {
        var result = new Vec3[PATH_POINTS];
        int last = points.size() - 1;
        for (int i = 0; i < PATH_POINTS; i++) {
            double position = (double) i * last / (PATH_POINTS - 1);
            int index = Math.min((int) position, Math.max(last - 1, 0));
            result[i] = last == 0 ? points.getFirst() : points.get(index).lerp(points.get(index + 1), position - index);
        }
        return result;
    }

    private boolean matches(Projectile source) {
        return !(source instanceof FishingHook) && !source.isNoGravity() && source.getGravity() > 0;
    }

    private Prediction held(Minecraft client, float partialTick) {
        var player = client.player;
        var shot = HeldThrow.find(player);
        if (shot == null) return null;
        Rotation packet = Paradox.ROTATIONS.packetRotation();
        Rotation rotation = packet != null ? packet
                : new Rotation(player.getViewYRot(partialTick), player.getViewXRot(partialTick));
        Vec3 start = player.getEyePosition(partialTick).add(0, -WeaponShot.SPAWN_OFFSET, 0);
        Vec3 velocity = shot.velocity(player, rotation);
        var box = new AABB(start, start).inflate(0.25);
        var path = ProjectileTrajectory.trace(start, velocity, shot.arrow(), shot.gravity(), 400,
                new Collisions(client.level, player, player, false, box, start, 0));
        return new Prediction(null, path, shot.icon());
    }

    private static ItemStack icon(Projectile source) {
        ItemStack stack = ItemStack.EMPTY;
        if (source instanceof ItemSupplier supplier) stack = supplier.getItem();
        else if (source instanceof AbstractArrow arrow) stack = arrow.getPickupItemStackOrigin();
        return stack.isEmpty() ? new ItemStack(Items.ARROW) : stack.copyWithCount(1);
    }

    private ProjectileTrajectory.Path predict(ClientLevel level, Projectile source, Vec3 position,
                                               Vec3 velocity, double gravity) {
        boolean arrow = source instanceof AbstractArrow;
        var owner = source.getOwner();
        boolean leftOwner = owner == null || ((ProjectileTrajectoryAccessor) source).paradox$leftOwner();
        return ProjectileTrajectory.trace(position, velocity, arrow, gravity, 400, new Collisions(level, source, owner,
                leftOwner, source.getBoundingBox(), source.position(), source.tickCount));
    }

    private static final class Collisions extends BlockCollisions {
        private final Entity owner;
        private final AABB bounds;
        private final Vec3 boundsOrigin;
        private final int age;
        private boolean leftOwner;

        private Collisions(ClientLevel level, Entity source, Entity owner, boolean leftOwner, AABB bounds,
                           Vec3 boundsOrigin, int age) {
            super(level, source);
            this.owner = owner;
            this.leftOwner = leftOwner;
            this.bounds = bounds;
            this.boundsOrigin = boundsOrigin;
            this.age = age;
        }

        @Override
        public ProjectileTrajectory.Impact collide(Vec3 from, Vec3 to, int tick) {
            var swept = bounds.move(from.subtract(boundsOrigin)).expandTowards(to.subtract(from)).inflate(1);
            if (!leftOwner) {
                leftOwner = owner.getRootVehicle().getSelfAndPassengers()
                        .filter(entity -> entity.isPickable() && !entity.isSpectator())
                        .noneMatch(entity -> swept.intersects(entity.getBoundingBox()));
            }
            var impact = super.collide(from, to, tick);
            Vec3 end = impact == null ? to : impact.position();
            double nearest = from.distanceToSqr(end);
            float margin = Math.clamp((age + tick - 2) / 20f, 0, 0.3f);
            for (var entity : level.getEntities(source, swept, entity -> entity.canBeHitByProjectile()
                    && (leftOwner || owner == null || !owner.isPassengerOfSameVehicle(entity)))) {
                var box = entity.getBoundingBox().inflate(margin);
                var hit = box.contains(from) ? java.util.Optional.of(from) : box.clip(from, end);
                if (hit.isEmpty()) continue;
                double distance = from.distanceToSqr(hit.get());
                if (distance <= nearest) {
                    nearest = distance;
                    impact = new ProjectileTrajectory.Impact(hit.get(), new Vec3(0, 1, 0));
                }
            }
            return impact;
        }
    }

    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!isEnabled() || !predictionTag.get() || client.player == null || client.level != sourceLevel
                || client.gui.hud.isHidden()) return;
        Vec3 cameraPosition = client.gameRenderer.mainCamera().position();
        float delta = tagClock.nextDelta();
        var active = new HashSet<UUID>();
        var visible = new ArrayList<Tag>();
        for (var prediction : predictions) {
            var path = prediction.path();
            if (!prediction.alive() || path.impact() == null) continue;
            UUID id = prediction.id();
            active.add(id);
            Vec3 point = path.impact().position().add(path.impact().normal().scale(0.04));
            var tag = tags.computeIfAbsent(id, ignored -> new Tag(point));
            tag.glide(point, delta);
            tag.text = String.format(Locale.ROOT, "%.1fs",
                    path.ticks() / Math.max(1, client.level.tickRateManager().tickrate()));
            tag.icon = prediction.icon();
            visible.add(tag);
        }
        tags.keySet().retainAll(active);
        visible.sort(Comparator.comparingDouble((Tag tag) -> tag.position.distanceToSqr(cameraPosition)).reversed());
        float scale = HudPlacement.scale(client);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale);
        try {
            for (var tag : visible) renderTag(client, graphics, tag, scale, delta);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void renderTag(Minecraft client, GuiGraphicsExtractor graphics, Tag tag, float scale, float delta) {
        var camera = client.gameRenderer.mainCamera();
        Vec3 point = tag.position;
        Vec3 relative = point.subtract(camera.position());
        var matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
        var projected = matrix.transform(
                new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1));
        if (!projected.isFinite() || projected.w <= 0.001f || projected.z < -projected.w || projected.z > projected.w) return;
        float centerX = (projected.x / projected.w + 1) * graphics.guiWidth() / scale / 2;
        float bottomY = (1 - projected.y / projected.w) * graphics.guiHeight() / scale / 2;
        if (centerX < 0 || centerX > graphics.guiWidth() / scale || bottomY < 0 || bottomY > graphics.guiHeight() / scale) return;
        Paradox.MODULES.get(NametagsModule.class).drawAt(graphics, tag.digits, tag.text, tag.icon, centerX, bottomY, DistanceScale.depth(projected.w, matrix), delta);
    }

    private void clear() {
        predictions = List.of();
        cache.clear();
        tags.clear();
        smoothed.clear();
        pathClock.reset();
        tagClock.reset();
        sourceLevel = null;
    }

    @Override
    protected void onDisable() {
        clear();
    }

    @EventHandler(priority = 40)
    private void onOverlayRender(OverlayRenderEvent event) {
        render(event.client(), event.graphics());
    }
}
