package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.IncomingPacketEvent;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.mixin.EntityRemovalInvoker;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.HeartsSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.AnchorDamage;
import cc.swelink.utils.combat.AttackUtils;
import cc.swelink.utils.combat.EnemyRaycast;
import cc.swelink.utils.combat.Targets;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationUtils;
import cc.swelink.utils.world.BlockInteractionController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class CrystalAuraModule extends Module {
    private static final double BLAST_REACH = 12;
    private static final float MIN_BLAST = 1;
    private static final float NEAR_BEST = 0.75f;
    private static final int ROTATION_PRIORITY = 4;
    private static final double INSET = 0.05;
    private static final int PROJECTION_STEPS = 24;
    private static final int FACE_SAMPLES = 5;
    private static final int MAX_PREDICT_TICKS = 12;
    private static final int INTERPOLATION_TICKS = 2;
    private static final int SPAWN_TIMEOUT_TICKS = 40;
    private static final long MIN_DEAD_TIMEOUT_MS = 150;
    private static final float OBSIDIAN_ADVANTAGE = 1.15f;
    private static final double BREAK_MARGIN = 0.1;
    private final IntSetting breakDelay = addSetting(new IntSetting("Break delay", 1, 1, 20));
    private final HeartsSetting maxSelfDamage = addSetting(new HeartsSetting("Max self damage", 8, 0, 40));
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final BooleanSetting hurtSync = addSetting(new BooleanSetting("Hurt sync", true));
    private final BlockInteractionController<Target> interaction = new BlockInteractionController<>(
            this, this::isEnabled, this::addSetting, true) {
        @Override
        protected Target findTarget(Minecraft client) {
            return CrystalAuraModule.this.findTarget(client);
        }

        @Override
        protected boolean validTarget(Minecraft client, Target target) {
            return CrystalAuraModule.this.validTarget(client, target);
        }

        @Override
        protected boolean matches(Target target, BlockHitResult hit) {
            return CrystalAuraModule.matches(target, hit);
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
            return RotationRaycast.blocks(rotation, client.player.blockInteractionRange(), ClipContext.Fluid.NONE,
                    only(target.hit().getBlockPos()));
        }

        @Override
        protected boolean useCamera() {
            return false;
        }

        @Override
        protected int rotationPriority() {
            return ROTATION_PRIORITY;
        }

        @Override
        protected boolean restoreAfterUse() {
            return false;
        }

        @Override
        protected boolean holdSelection() {
            var client = Minecraft.getInstance();
            return client.player != null && InventoryUtils.findHotbar(client.player.getInventory(), Items.END_CRYSTAL) >= 0
                    && AnchorAuraModule.closestEnemy(client, raycast.target()) != null && !yields(client);
        }

        @Override
        protected InteractionResult interact(Minecraft client, Target target, InteractionHand hand,
                                             BlockHitResult hit, Rotation rotation) {
            InteractionResult result = super.interact(client, target, hand, hit, rotation);
            if (!target.placing()) {
                pendingSpawn = target.position().above();
                pendingSpawnTick = ticks;
            }
            return result;
        }
    };
    private final Map<Integer, Dead> dead = new HashMap<>();
    private long ticks;
    private long nextBreak;
    private Hold hold;
    private float roundTrip = 2;
    private BlockPos pendingSpawn;
    private long pendingSpawnTick;
    private float planDamage;
    private float potential;
    private int potentialTick = -1;

    private record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item, boolean placing)
            implements BlockInteractionController.BlockTarget {
    }

    private record Candidate(BlockPos base, boolean obsidian, float damage) {
    }

    private record Choice(Target target, Hold hold) {
    }

    private record Breakable(EndCrystal crystal, Vec3 aim, Player enemy) {
    }

    private record Aim(HitResult hit, double angle) {
    }

    private record Hold(AABB box, double range, Function<Rotation, HitResult> trace, BooleanSupplier valid) {
    }

    private record Dead(EndCrystal crystal, ClientLevel level, long expires) {
    }

    public CrystalAuraModule() {
        super("CrystalAura", "Crystals close to players", Category.COMBAT);
    }

    public float potential(Minecraft client) {
        if (!isEnabled() || client.player == null || client.level == null) return 0;
        if (potentialTick != client.player.tickCount) {
            Hold previous = hold;
            plan(client);
            hold = previous;
            remember(client);
        }
        return potential;
    }

    private void remember(Minecraft client) {
        potentialTick = client.player.tickCount;
        potential = planDamage;
    }

    private boolean yields(Minecraft client) {
        return Paradox.MODULES.get(AnchorAuraModule.class).potential(client) > potential(client);
    }

    private Target findTarget(Minecraft client) {
        Target target = plan(client);
        remember(client);
        if (target != null && Paradox.MODULES.get(AnchorAuraModule.class).potential(client) > planDamage) {
            hold = null;
            return null;
        }
        return target;
    }

    private Target plan(Minecraft client) {
        planDamage = 0;
        Player enemy = AnchorAuraModule.closestEnemy(client, raycast.target());
        Breakable existing = enemy == null ? null : findCrystal(client, enemy);
        if (existing != null) {
            planDamage = AnchorDamage.crystalHealthDamage(client, enemy, existing.crystal().position());
            return null;
        }
        if (awaitingSpawn()) return null;
        hold = null;
        if (enemy == null || InventoryUtils.findHotbar(client.player.getInventory(), Items.END_CRYSTAL) < 0) return null;
        Rotation from = interaction.rotations().current(client);
        double range = client.player.blockInteractionRange();
        boolean obsidian = InventoryUtils.findHotbar(client.player.getInventory(), Items.OBSIDIAN) >= 0;
        var candidates = candidates(client, enemy, range, obsidian, predictedOffset(client, enemy, placeTicks()));
        Choice chosen = null;
        for (Candidate candidate : candidates) {
            chosen = candidate.obsidian() ? placement(client, from, range, candidate) : crystalTarget(client, from, range, candidate);
            if (chosen != null) {
                planDamage = candidate.damage();
                break;
            }
        }
        if (chosen == null) return null;
        Paradox.TARGETS.report(enemy);
        hold = chosen.hold();
        return chosen.target();
    }

    private List<Candidate> candidates(Minecraft client, Player enemy, double range, boolean obsidian, Vec3 offset) {
        var level = client.level;
        Vec3 eyes = client.player.getEyePosition();
        int radius = (int) Math.ceil(range) + 1;
        BlockPos origin = BlockPos.containing(eyes);
        Vec3 center = enemy.getBoundingBox().getCenter().add(offset);
        int highest = Mth.floor(enemy.getY());
        int lowest = highest - 3;
        var candidates = new ArrayList<Candidate>();
        double breakRange = client.player.entityInteractionRange() - BREAK_MARGIN;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (eyes.distanceTo(RotationUtils.nearestPoint(eyes, new AABB(pos))) > range
                    || eyes.distanceTo(RotationUtils.nearestPoint(eyes, crystalBox(pos))) > breakRange
                    || Vec3.atBottomCenterOf(pos.above()).distanceToSqr(enemy.position()) > BLAST_REACH * BLAST_REACH) continue;
            var state = level.getBlockState(pos);
            boolean existing = base(state);
            if (!existing && (!obsidian || pos.getY() < lowest || pos.getY() > highest || !state.canBeReplaced()
                    || !level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, CollisionContext.empty())
                    || !AnchorAuraModule.clearOfPlayers(client, pos))) continue;
            if (!AutoCrystalModule.canPlaceCrystal(client, pos)) continue;
            float damage = damage(client, enemy, pos, !existing, offset);
            if (damage > 0) candidates.add(new Candidate(pos.immutable(), !existing, damage));
        }
        float best = 0;
        float bestExisting = 0;
        for (Candidate candidate : candidates) {
            best = Math.max(best, candidate.damage());
            if (!candidate.obsidian()) bestExisting = Math.max(bestExisting, candidate.damage());
        }
        float floor = best * NEAR_BEST;
        candidates.removeIf(candidate -> candidate.damage() < floor);
        if (bestExisting >= floor && best < bestExisting * OBSIDIAN_ADVANTAGE) candidates.removeIf(Candidate::obsidian);
        candidates.sort(Comparator.<Candidate>comparingDouble(candidate -> Vec3.atCenterOf(candidate.base().above()).distanceToSqr(center))
                .thenComparing(Candidate::obsidian));
        return candidates;
    }

    private float damage(Minecraft client, Player enemy, BlockPos base, boolean placing, Vec3 offset) {
        BlockPos above = base.above();
        if (!crystalSpaceClear(client, above, enemy, offset)) return 0;
        Vec3 crystal = Vec3.atBottomCenterOf(above);
        BlockPos shield = placing ? base : null;
        if (AnchorDamage.crystalDamage(enemy, crystal, offset) <= MIN_BLAST || !safeForSelf(client, crystal, shield)) return 0;
        return Math.max(AnchorDamage.crystalHealthDamage(client, enemy, crystal, shield, offset), 0.001f);
    }

    private static AABB crystalBox(BlockPos base) {
        Vec3 bottom = Vec3.atBottomCenterOf(base.above());
        return new AABB(bottom.x - 1, bottom.y, bottom.z - 1, bottom.x + 1, bottom.y + 2, bottom.z + 1);
    }

    private static boolean crystalSpaceClear(Minecraft client, BlockPos above, Player enemy, Vec3 offset) {
        AABB column = new AABB(above).expandTowards(0, 1, 0);
        for (var player : client.level.players()) {
            if (player == client.player || player.isSpectator()) continue;
            AABB box = player == enemy ? player.getBoundingBox().expandTowards(offset) : player.getBoundingBox();
            if (box.intersects(column)) return false;
        }
        return true;
    }

    private boolean awaitingSpawn() {
        return pendingSpawn != null && ticks - pendingSpawnTick <= Math.round(roundTrip * 1.5f) + 2;
    }

    private int placeTicks() {
        return Math.min(MAX_PREDICT_TICKS, Math.round(roundTrip * 2) + INTERPOLATION_TICKS);
    }

    private int breakTicks() {
        return Math.min(MAX_PREDICT_TICKS, Math.round(roundTrip) + INTERPOLATION_TICKS);
    }

    private static Vec3 predictedOffset(Minecraft client, Player enemy, int ticks) {
        Vec3 motion = new Vec3(enemy.getX() - enemy.xo, 0, enemy.getZ() - enemy.zo);
        if (motion.lengthSqr() > 1) motion = motion.normalize();
        Vec3 offset = motion.scale(ticks);
        for (int i = 0; i < 4 && offset.lengthSqr() > 1.0E-4; i++) {
            if (client.level.noCollision(enemy, enemy.getBoundingBox().move(offset))) return offset;
            offset = offset.scale(0.5);
        }
        return Vec3.ZERO;
    }

    private Choice crystalTarget(Minecraft client, Rotation from, double range, Candidate candidate) {
        BlockPos base = candidate.base();
        AABB box = new AABB(base);
        Function<Rotation, HitResult> trace = rotation -> blockHit(client, rotation, base, null);
        Aim aim = closestAim(client, from, box, range, trace);
        if (aim == null) return null;
        BlockHitResult hit = (BlockHitResult) aim.hit();
        return new Choice(new Target(base, hit, hit.getLocation(), Items.END_CRYSTAL, false), new Hold(box, range, trace,
                () -> base(client.level.getBlockState(base)) && AutoCrystalModule.canPlaceCrystal(client, base)));
    }

    private Choice placement(Minecraft client, Rotation from, double range, Candidate candidate) {
        var level = client.level;
        BlockPos spot = candidate.base();
        Aim best = null;
        Hold bestHold = null;
        for (Direction direction : Direction.values()) {
            BlockPos support = spot.relative(direction);
            var state = level.getBlockState(support);
            if (state.canBeReplaced() || state.getShape(level, support).isEmpty() || SafeAnchorModule.usable(state)) continue;
            AABB face = face(spot, direction);
            Function<Rotation, HitResult> trace = rotation -> blockHit(client, rotation, support, spot);
            Aim aim = closestAim(client, from, face, range, trace);
            if (aim != null && (best == null || aim.angle() < best.angle())) {
                best = aim;
                bestHold = new Hold(face, range, trace, () -> level.getBlockState(spot).canBeReplaced());
            }
        }
        if (best == null) return null;
        BlockHitResult hit = (BlockHitResult) best.hit();
        return new Choice(new Target(spot, hit, hit.getLocation(), Items.OBSIDIAN, true), bestHold);
    }

    private static AABB face(BlockPos spot, Direction direction) {
        AABB block = new AABB(spot);
        Vec3 center = Vec3.atCenterOf(spot).add(direction.getStepX() * 0.5, direction.getStepY() * 0.5, direction.getStepZ() * 0.5);
        return switch (direction.getAxis()) {
            case X -> new AABB(center.x, block.minY, block.minZ, center.x, block.maxY, block.maxZ);
            case Y -> new AABB(block.minX, center.y, block.minZ, block.maxX, center.y, block.maxZ);
            case Z -> new AABB(block.minX, block.minY, center.z, block.maxX, block.maxY, center.z);
        };
    }

    private BlockPos only(BlockPos pos) {
        return raycast.target() ? pos : null;
    }

    private HitResult blockHit(Minecraft client, Rotation rotation, BlockPos block, BlockPos placed) {
        BlockHitResult hit = RotationRaycast.blocks(rotation, client.player.blockInteractionRange(), ClipContext.Fluid.NONE, only(block));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(block)) return null;
        return placed == null || hit.getBlockPos().relative(hit.getDirection()).equals(placed) ? hit : null;
    }

    private static Aim closestAim(Minecraft client, Rotation from, AABB box, double range,
                                  Function<Rotation, HitResult> trace) {
        Vec3 eyes = client.player.getEyePosition();
        if (eyes.distanceTo(RotationUtils.nearestPoint(eyes, box)) > range) return null;
        HitResult current = trace.apply(from);
        if (current != null) return new Aim(current, 0);
        AABB inner = inset(box);
        Rotation nearest = RotationUtils.lookAt(eyes, nearestOnCone(eyes, from.direction(), inner));
        HitResult direct = trace.apply(nearest);
        if (direct != null) return new Aim(direct, angle(from, nearest));
        return sampledAim(eyes, from, box, range, trace);
    }

    private static Aim sampledAim(Vec3 eyes, Rotation from, AABB box, double range, Function<Rotation, HitResult> trace) {
        Aim best = null;
        for (Direction face : Direction.values()) {
            double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(face.getAxis()) : box.min(face.getAxis());
            double eye = face.getAxis().choose(eyes.x, eyes.y, eyes.z);
            if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? eye <= plane : eye >= plane) continue;
            for (int u = 0; u < FACE_SAMPLES; u++) {
                for (int v = 0; v < FACE_SAMPLES; v++) {
                    Vec3 point = facePoint(box, face, plane, (u + 0.5) / FACE_SAMPLES, (v + 0.5) / FACE_SAMPLES);
                    if (eyes.distanceTo(point) > range) continue;
                    Rotation rotation = RotationUtils.lookAt(eyes, point);
                    double angle = angle(from, rotation);
                    if (best != null && angle >= best.angle()) continue;
                    HitResult hit = trace.apply(rotation);
                    if (hit != null) best = new Aim(hit, angle);
                }
            }
        }
        return best;
    }

    private static Vec3 facePoint(AABB box, Direction face, double plane, double u, double v) {
        return switch (face.getAxis()) {
            case X -> new Vec3(plane, Mth.lerp(u, box.minY, box.maxY), Mth.lerp(v, box.minZ, box.maxZ));
            case Y -> new Vec3(Mth.lerp(u, box.minX, box.maxX), plane, Mth.lerp(v, box.minZ, box.maxZ));
            case Z -> new Vec3(Mth.lerp(u, box.minX, box.maxX), Mth.lerp(v, box.minY, box.maxY), plane);
        };
    }

    private static Vec3 nearestOnCone(Vec3 eyes, Vec3 look, AABB box) {
        Vec3 point = RotationUtils.nearestPoint(eyes.add(look.scale(eyes.distanceTo(box.getCenter()))), box);
        for (int i = 0; i < PROJECTION_STEPS; i++) {
            Vec3 offset = point.subtract(eyes);
            double along = offset.dot(look);
            if (along <= 1.0E-6) break;
            Vec3 next = RotationUtils.nearestPoint(eyes.add(look.scale(offset.lengthSqr() / along)), box);
            if (next.distanceToSqr(point) < 1.0E-10) break;
            point = next;
        }
        return point;
    }

    private static AABB inset(AABB box) {
        double x = Math.min(INSET, box.getXsize() / 2);
        double y = Math.min(INSET, box.getYsize() / 2);
        double z = Math.min(INSET, box.getZsize() / 2);
        return new AABB(box.minX + x, box.minY + y, box.minZ + z, box.maxX - x, box.maxY - y, box.maxZ - z);
    }

    private static double angle(Rotation from, Rotation to) {
        return Math.hypot(RotationUtils.yawDifference(from.yaw(), to.yaw()), to.pitch() - from.pitch());
    }

    static boolean base(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    private boolean safeForSelf(Minecraft client, Vec3 crystal) {
        return safeForSelf(client, crystal, null);
    }

    private boolean safeForSelf(Minecraft client, Vec3 crystal, BlockPos shield) {
        var player = client.player;
        if (AnchorDamage.harmless(player)) return true;
        float damage = AnchorDamage.crystalHealthDamage(client, player, crystal, shield);
        return damage <= maxSelfDamage.get();
    }

    private Breakable findCrystal(Minecraft client, Player enemy) {
        var player = client.player;
        var crystals = client.level.getEntitiesOfClass(EndCrystal.class,
                player.getBoundingBox().inflate(player.entityInteractionRange()), EndCrystal::isAlive);
        crystals.sort(Comparator.comparingDouble(crystal -> crystal.distanceToSqr(enemy)));
        Vec3 offset = predictedOffset(client, enemy, breakTicks());
        for (EndCrystal crystal : crystals) {
            if (dead.containsKey(crystal.getId()) || AnchorDamage.crystalDamage(enemy, crystal.position(), offset) <= MIN_BLAST
                    || !safeForSelf(client, crystal.position())) continue;
            double range = player.entityInteractionRange();
            Function<Rotation, HitResult> trace = rotation -> crystalHit(client, crystal, rotation);
            Aim aim = closestAim(client, interaction.rotations().current(client), crystal.getBoundingBox(), range, trace);
            if (aim != null) {
                hold = new Hold(crystal.getBoundingBox(), range, trace, crystal::isAlive);
                return new Breakable(crystal, aim.hit().getLocation(), enemy);
            }
        }
        return null;
    }

    private HitResult crystalHit(Minecraft client, EndCrystal crystal, Rotation rotation) {
        var player = client.player;
        if (raycast.target()) return EnemyRaycast.trace(client, crystal, rotation);
        return RotationRaycast.trace(rotation, player.blockInteractionRange(), player.entityInteractionRange(),
                ClipContext.Fluid.NONE, Targets::pickable) instanceof EntityHitResult hit
                && hit.getEntity() == crystal && AttackUtils.isInRange(player, hit) ? hit : null;
    }

    private boolean looksAt(Minecraft client, EndCrystal crystal, Rotation rotation) {
        return crystalHit(client, crystal, rotation) != null;
    }

    private boolean canBreak(Minecraft client) {
        return BlockInteractionController.active(client) && (!SlotSelection.isOwned()
                || SlotSelection.owns(this, client.player.getInventory()));
    }

    private Breakable breakable(Minecraft client) {
        if (!canBreak(client)) return null;
        Player enemy = AnchorAuraModule.closestEnemy(client, raycast.target());
        Breakable crystal = enemy == null ? null : findCrystal(client, enemy);
        if (crystal != null) Paradox.TARGETS.report(enemy);
        return crystal;
    }

    private void aimAtCrystal(Minecraft client) {
        Breakable target = breakable(client);
        if (target == null) return;
        interaction.rotations().request(client, RotationUtils.lookAt(client.player.getEyePosition(), target.aim()), ROTATION_PRIORITY);
    }

    private void breakCrystal(Minecraft client) {
        if (ticks < nextBreak) return;
        Breakable target = breakable(client);
        if (target == null || hurtSync.get() && !Targets.hurtReady(client, target.enemy())) return;
        var player = client.player;
        if (!looksAt(client, target.crystal(), interaction.rotations().current(client))) return;
        var stack = player.getMainHandItem();
        if (!stack.isItemEnabled(client.level.enabledFeatures()) || player.cannotAttackWithItem(stack, 0)) return;
        if (AttackUtils.attack(client, target.crystal())) {
            nextBreak = ticks + breakDelay.get();
            markDead(client, target.crystal());
        }
    }

    private void markDead(Minecraft client, EndCrystal crystal) {
        long timeout = Math.max(MIN_DEAD_TIMEOUT_MS, (long) (roundTrip * 100) + 100) * 1_000_000L;
        dead.put(crystal.getId(), new Dead(crystal, client.level, System.nanoTime() + timeout));
        client.level.removeEntity(crystal.getId(), Entity.RemovalReason.DISCARDED);
    }

    private void restoreDead(Minecraft client, boolean all) {
        if (dead.isEmpty()) return;
        long now = System.nanoTime();
        var iterator = dead.values().iterator();
        while (iterator.hasNext()) {
            Dead entry = iterator.next();
            if (!all && now < entry.expires()) continue;
            iterator.remove();
            if (client.level != null && client.level == entry.level() && client.level.getEntity(entry.crystal().getId()) == null) {
                ((EntityRemovalInvoker) entry.crystal()).paradox$unsetRemoved();
                client.level.addEntity(entry.crystal());
            }
        }
    }

    private void incoming(IncomingPacketEvent event) {
        if (event.packet() instanceof ClientboundRemoveEntitiesPacket remove) {
            for (int id : remove.entityIds()) dead.remove(id);
        } else if (event.packet() instanceof ClientboundAddEntityPacket add && pendingSpawn != null
                && add.getType() == EntityTypes.END_CRYSTAL
                && BlockPos.containing(add.getX(), add.getY(), add.getZ()).equals(pendingSpawn)) {
            roundTrip = roundTrip * 0.7f + Math.max(1, ticks - pendingSpawnTick) * 0.3f;
            pendingSpawn = null;
        }
    }

    private boolean validTarget(Minecraft client, Target target) {
        var state = client.level.getBlockState(target.position());
        if (target.placing() ? !state.canBeReplaced() || !AnchorAuraModule.clearOfPlayers(client, target.position())
                : !base(state) || !AutoCrystalModule.canPlaceCrystal(client, target.position())) return false;
        var eyes = client.player.getEyePosition();
        return matches(target, RotationRaycast.blocks(RotationUtils.lookAt(eyes, target.aim()),
                client.player.blockInteractionRange(), ClipContext.Fluid.NONE, only(target.hit().getBlockPos())));
    }

    private static boolean matches(Target target, BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(target.hit().getBlockPos())) return false;
        return !target.placing() || hit.getDirection() == target.hit().getDirection();
    }

    @Override
    public void tick(Minecraft client) {
        ticks++;
        restoreDead(client, false);
        if (pendingSpawn != null && ticks - pendingSpawnTick > SPAWN_TIMEOUT_TICKS) pendingSpawn = null;
        interaction.tick(client);
        aimAtCrystal(client);
        holdAim(client);
    }

    private void holdAim(Minecraft client) {
        if (hold == null) return;
        var player = client.player;
        Vec3 eyes = player.getEyePosition();
        if (!canBreak(client) || AnchorAuraModule.closestEnemy(client, raycast.target()) == null
                || InventoryUtils.findHotbar(player.getInventory(), Items.END_CRYSTAL) < 0
                || eyes.distanceTo(RotationUtils.nearestPoint(eyes, hold.box())) > hold.range() || !hold.valid().getAsBoolean()) {
            hold = null;
            return;
        }
        Aim aim = closestAim(client, interaction.rotations().current(client), hold.box(), hold.range(), hold.trace());
        if (aim == null) {
            hold = null;
            return;
        }
        interaction.rotations().request(client, RotationUtils.lookAt(eyes, aim.hit().getLocation()), ROTATION_PRIORITY);
    }

    @Override
    public void tickInput(Minecraft client) {
        interaction.tickInput(client);
        breakCrystal(client);
    }

    private void manualUse(ManualUseEvent event) {
        if (!interaction.allowManualUse(event.client())) event.cancel();
    }

    @Override
    protected void onDisable() {
        hold = null;
        pendingSpawn = null;
        restoreDead(Minecraft.getInstance(), true);
        interaction.disable();
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 217)
    private void onPreTick(PreTickEvent event) {
        interaction.tickSwap(event.client());
    }

    @EventHandler(priority = 37)
    private void onManualUse(ManualUseEvent event) {
        manualUse(event);
    }

    @EventHandler(priority = 37)
    private void onTickBoundary(TickBoundaryEvent event) {
        interaction.afterTickBoundary();
    }

    @EventHandler(priority = 37)
    private void onIncomingPacket(IncomingPacketEvent event) {
        incoming(event);
    }
}
