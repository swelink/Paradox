package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.ManualUseEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.events.TickBoundaryEvent;
import cc.swelink.mixin.ArrowTrajectoryAccessor;
import cc.swelink.settings.RaycastSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.TargetFinder;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.projectile.BlockCollisions;
import cc.swelink.utils.projectile.ProjectileTrajectory;
import cc.swelink.utils.projectile.WeaponShot;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationRaycast;
import cc.swelink.utils.rotation.RotationUtils;
import cc.swelink.utils.world.BlockInteractionController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CartAuraModule extends Module {
    private static final List<Item> RAILS = List.of(Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL);
    private static final int ROTATION_PRIORITY = 5;
    private static final double NEXT_TO_ENEMY = 2;
    private static final double CART_NEAR_ENEMY = 2.5;
    private static final int MIN_DRAW = 3;
    private static final int MAX_DRAW = 20;
    private static final int GIVE_UP_TICKS = 3;
    private static final int ARROW_WAIT = 10;
    private static final int MAX_FLIGHT = 40;
    private static final double ARROW_GRAVITY = 0.05;
    private static final double BOW_POWER = 3;
    private static final double AIM_TOLERANCE = 1;
    private static final double CART_HEIGHT = 0.0625;
    private static final double CART_WIDTH = 0.98;
    private static final double CART_TALL = 0.7;
    private static final int PLACE_LEAD = 3;
    private final RaycastSetting raycast = addSetting(new RaycastSetting());
    private final BlockInteractionController<Target> interaction = new BlockInteractionController<>(
            this, this::isEnabled, this::addSetting, true) {
        @Override
        protected Target findTarget(Minecraft client) {
            return CartAuraModule.this.findTarget(client);
        }

        @Override
        protected boolean validTarget(Minecraft client, Target target) {
            Target fresh = CartAuraModule.this.findTarget(client);
            return fresh != null && fresh.position().equals(target.position()) && fresh.item() == target.item();
        }

        @Override
        protected boolean matches(Target target, BlockHitResult hit) {
            return hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target.hit().getBlockPos())
                    && (target.item() == Items.TNT_MINECART || hit.getDirection() == target.hit().getDirection());
        }

        @Override
        protected BlockHitResult traceTarget(Minecraft client, Target target, Rotation rotation) {
            return RotationRaycast.blocks(rotation, client.player.blockInteractionRange(), ClipContext.Fluid.NONE,
                    only(target.hit().getBlockPos()));
        }

        @Override
        protected int rotationPriority() {
            return ROTATION_PRIORITY;
        }
    };
    private Shot shot;
    private boolean switching;
    private boolean drawing;
    private boolean holdingUse;
    private boolean restorePending;
    private int boundary;
    private int releaseBoundary;
    private int switchedAt;
    private int nextShot;
    private int releasedAt = -ARROW_WAIT;
    private int originalSlot = -1;
    private int bowSlot = -1;
    private BlockPos armedSpot;
    private int armedUntil;

    private record Target(BlockPos position, BlockHitResult hit, Vec3 aim, Item item)
            implements BlockInteractionController.BlockTarget {
    }

    private record Shot(Player enemy, MinecartTNT cart, BlockPos spot, Rotation rotation, int draw, int flight) {
    }

    public CartAuraModule() {
        super("CartAura", "Place a rail and TNT minecart next to the closest player and shoot it with a flame bow.",
                Category.COMBAT);
        interaction.setDefaultDelays(0, 0);
    }

    private Target findTarget(Minecraft client) {
        if (switching || drawing || client.player == null || client.level == null) return null;
        for (Player enemy : enemies(client)) {
            if (cartNear(client, enemy) != null) return null;
            for (BlockPos spot : spots(client, enemy)) {
                Target target = placement(client, spot);
                if (target != null && !armed(client, spot)) return null;
                if (target != null) {
                    Paradox.TARGETS.report(enemy);
                    return target;
                }
            }
        }
        return null;
    }

    private Target placement(Minecraft client, BlockPos spot) {
        var player = client.player;
        var level = client.level;
        var eyes = player.getEyePosition();
        double reach = player.blockInteractionRange();
        var state = level.getBlockState(spot);
        if (state.getBlock() instanceof BaseRailBlock) {
            if (InventoryUtils.findHotbar(player.getInventory(), Items.TNT_MINECART) < 0) return null;
            Vec3 aim = new Vec3(spot.getX() + 0.5, spot.getY() + 0.05, spot.getZ() + 0.5);
            var hit = RotationRaycast.blocks(RotationUtils.lookAt(eyes, aim), reach, ClipContext.Fluid.NONE, only(spot));
            if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(spot)) return null;
            return new Target(spot, hit, hit.getLocation(), Items.TNT_MINECART);
        }
        Item rail = rail(client);
        if (rail == null || InventoryUtils.findHotbar(player.getInventory(), Items.TNT_MINECART) < 0) return null;
        if (!level.getEntities(null, new AABB(spot)).isEmpty()) return null;
        BlockPos floor = spot.below();
        Vec3 aim = Vec3.atCenterOf(floor).add(0, 0.5, 0);
        var hit = RotationRaycast.blocks(RotationUtils.lookAt(eyes, aim), reach, ClipContext.Fluid.NONE, only(floor));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(floor)
                || hit.getDirection() != Direction.UP) return null;
        return new Target(spot, hit, hit.getLocation(), rail);
    }

    private List<Player> enemies(Minecraft client) {
        var player = client.player;
        double reach = player.blockInteractionRange() + NEXT_TO_ENEMY;
        double limit = reach * reach;
        return TargetFinder.sorted(client, raycast.target(), target -> player.distanceToSqr(target) <= limit,
                player::distanceToSqr);
    }

    private List<BlockPos> spots(Minecraft client, Player enemy) {
        var level = client.level;
        var eyes = client.player.getEyePosition();
        double reach = client.player.blockInteractionRange() - 0.2;
        BlockPos feet = enemy.blockPosition();
        var spots = new ArrayList<BlockPos>();
        for (BlockPos candidate : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            BlockPos spot = candidate.immutable();
            var state = level.getBlockState(spot);
            boolean rail = state.getBlock() instanceof BaseRailBlock;
            if (!rail && (!state.canBeReplaced() || !level.getFluidState(spot).isEmpty())) continue;
            BlockPos floor = spot.below();
            if (!rail && !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) continue;
            if (eyes.distanceTo(Vec3.atCenterOf(floor).add(0, 0.5, 0)) > reach) continue;
            spots.add(spot);
        }
        Vec3 enemyFeet = enemy.position();
        spots.sort(Comparator.comparing((BlockPos spot) -> !(level.getBlockState(spot).getBlock() instanceof BaseRailBlock))
                .thenComparingDouble(spot -> Vec3.atBottomCenterOf(spot).distanceToSqr(enemyFeet)));
        return spots;
    }

    private MinecartTNT cartNear(Minecraft client, Player enemy) {
        MinecartTNT best = null;
        double bestDistance = CART_NEAR_ENEMY * CART_NEAR_ENEMY;
        for (var cart : client.level.getEntitiesOfClass(MinecartTNT.class, enemy.getBoundingBox().inflate(CART_NEAR_ENEMY))) {
            double distance = cart.distanceToSqr(enemy);
            if (cart.isAlive() && distance <= bestDistance) {
                best = cart;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public void tick(Minecraft client) {
        if (drawing && shot != null && client.player != null) {
            var aimed = aim(client.player, box(shot), speed(shot.draw()), shot.cart() == null ? lead(client, shot.spot()) : 0);
            if (aimed != null) shot = new Shot(shot.enemy(), shot.cart(), shot.spot(), aimed.rotation(), shot.draw(), aimed.flight());
            Paradox.TARGETS.report(shot.enemy());
            interaction.rotations().request(client, shot.rotation(), ROTATION_PRIORITY);
            return;
        }
        interaction.tick(client);
    }

    @Override
    public void tickInput(Minecraft client) {
        if (!ready(client)) {
            stop(client);
            return;
        }
        if (restorePending) return;
        var player = client.player;
        if (drawing) {
            if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof BowItem)
                    || !SlotSelection.owns(this, player.getInventory()) || shot == null || !shotValid(client, shot)) {
                stop(client);
                return;
            }
            client.options.keyUse.setDown(true);
            int ticks = player.getTicksUsingItem();
            if (ticks < shot.draw()) return;
            Rotation sent = Paradox.ROTATIONS.serverRotation();
            boolean lined = sent != null && Math.abs(RotationUtils.yawDifference(sent.yaw(), shot.rotation().yaw())) <= AIM_TOLERANCE
                    && Math.abs(sent.pitch() - shot.rotation().pitch()) <= AIM_TOLERANCE;
            if (lined || ticks >= shot.draw() + GIVE_UP_TICKS) stop(client);
            return;
        }
        if (!switching) {
            interaction.tickInput(client);
            if (interaction.hasTarget() || SlotSelection.isOwned()) return;
            boolean flying = arrowInFlight(client);
            if (flying) releasedAt = player.tickCount - ARROW_WAIT;
            if (player.tickCount < nextShot || flying || player.tickCount - releasedAt < ARROW_WAIT) return;
            if (Paradox.MODULES.get(SafeCartModule.class).busy(client)) return;
            int slot = InventoryUtils.findHotbar(player.getInventory(), stack -> flameBow(client, stack));
            if (slot < 0 || player.getProjectile(player.getInventory().getItem(slot)).isEmpty() || findShot(client) == null) return;
            var inventory = player.getInventory();
            if (!SlotSelection.begin(this, inventory)) return;
            originalSlot = inventory.getSelectedSlot();
            bowSlot = slot;
            if (!SlotSelection.select(this, inventory, slot)) {
                SlotSelection.complete(this, -1);
                bowSlot = originalSlot = -1;
                return;
            }
            SlotSelection.syncCarriedItem(client);
            switching = true;
            switchedAt = player.tickCount;
        }
        if (!SlotSelection.owns(this, player.getInventory()) || player.getInventory().getSelectedSlot() != bowSlot) {
            stop(client);
            return;
        }
        if (player.tickCount - switchedAt < interaction.switchDelayTicks()) return;
        Shot found = findShot(client);
        if (found == null) {
            stop(client);
            return;
        }
        switching = false;
        if (!(client.gameMode.useItem(player, InteractionHand.MAIN_HAND) instanceof InteractionResult.Success)) {
            stop(client);
            return;
        }
        shot = found;
        drawing = true;
        holdingUse = true;
        client.options.keyUse.setDown(true);
    }

    private Shot findShot(Minecraft client) {
        var player = client.player;
        for (Player enemy : enemies(client)) {
            MinecartTNT cart = cartNear(client, enemy);
            BlockPos spot = cart == null ? shotSpot(client, enemy) : null;
            if (cart == null && spot == null) continue;
            var box = cart != null ? cart.getBoundingBox() : cartBox(spot);
            int lead = cart == null ? lead(client, spot) : 0;
            for (int draw = MIN_DRAW; draw <= MAX_DRAW; draw++) {
                var aimed = aim(player, box, speed(draw), lead);
                if (aimed != null) return new Shot(enemy, cart, spot, aimed.rotation(), draw, aimed.flight());
            }
        }
        return null;
    }

    private BlockPos shotSpot(Minecraft client, Player enemy) {
        for (BlockPos spot : spots(client, enemy)) {
            if (placement(client, spot) != null) return spot;
        }
        return null;
    }

    private boolean shotValid(Minecraft client, Shot shot) {
        if (shot.cart() != null) return shot.cart().isAlive();
        return placement(client, shot.spot()) != null && cartNear(client, shot.enemy()) == null;
    }

    private boolean armed(Minecraft client, BlockPos spot) {
        return spot.equals(armedSpot) && client.player.tickCount < armedUntil;
    }

    private int lead(Minecraft client, BlockPos spot) {
        int placements = client.level.getBlockState(spot).getBlock() instanceof BaseRailBlock ? 1 : 2;
        return placements * (interaction.switchDelayTicks() + interaction.placeDelayTicks() + PLACE_LEAD) + PLACE_LEAD;
    }

    private static AABB box(Shot shot) {
        return shot.cart() != null ? shot.cart().getBoundingBox() : cartBox(shot.spot());
    }

    private static AABB cartBox(BlockPos spot) {
        double half = CART_WIDTH / 2;
        double bottom = spot.getY() + CART_HEIGHT;
        return new AABB(spot.getX() + 0.5 - half, bottom, spot.getZ() + 0.5 - half,
                spot.getX() + 0.5 + half, bottom + CART_TALL, spot.getZ() + 0.5 + half);
    }

    private record Aim(Rotation rotation, int flight) {
    }

    private Aim aim(LocalPlayer player, AABB box, double speed, int minFlight) {
        Vec3 start = start(player);
        Vec3 point = box.getCenter();
        var delta = point.subtract(start);
        double distance = delta.horizontalDistance();
        if (distance < 0.3) return null;
        Double steep = highArc(distance, delta.y, speed);
        if (steep == null) return null;
        Rotation rotation = new Rotation(RotationUtils.lookAt(start, point).yaw(), (float) -Math.toDegrees(steep));
        int flight = flight(player, box, rotation, speed);
        return flight >= minFlight ? new Aim(rotation, flight) : null;
    }

    private static int flight(LocalPlayer player, AABB target, Rotation rotation, double speed) {
        var box = target.inflate(0.1);
        var hit = new boolean[1];
        var velocity = rotation.direction().scale(speed).add(new WeaponShot(0, ARROW_GRAVITY, true).inherited(player));
        var path = ProjectileTrajectory.trace(start(player), velocity, true, ARROW_GRAVITY, MAX_FLIGHT,
                new BlockCollisions(player.level(), player) {
                    @Override
                    public ProjectileTrajectory.Impact collide(Vec3 from, Vec3 to, int tick) {
                        var block = super.collide(from, to, tick);
                        Optional<Vec3> entry = box.contains(from) ? Optional.of(from)
                                : box.clip(from, block == null ? to : block.position());
                        if (entry.isEmpty()) return block;
                        hit[0] = true;
                        return new ProjectileTrajectory.Impact(entry.get(), new Vec3(0, 1, 0));
                    }
                });
        return hit[0] ? (int) Math.floor(path.ticks()) : -1;
    }

    private static Double highArc(double distance, double height, double speed) {
        double previous = Math.toRadians(89);
        for (int degrees = 88; degrees >= 20; degrees--) {
            double angle = Math.toRadians(degrees);
            var impact = fly(angle, distance, speed).impact();
            if (impact != null && impact.position().y >= height) {
                double low = angle;
                double up = previous;
                for (int step = 0; step < 12; step++) {
                    double middle = (low + up) / 2;
                    var probe = fly(middle, distance, speed).impact();
                    if (probe != null && probe.position().y >= height) low = middle;
                    else up = middle;
                }
                return low;
            }
            previous = angle;
        }
        return null;
    }

    private static ProjectileTrajectory.Path fly(double angle, double distance, double speed) {
        var velocity = new Vec3(Math.cos(angle) * speed, Math.sin(angle) * speed, 0);
        return ProjectileTrajectory.trace(Vec3.ZERO, velocity, true, ARROW_GRAVITY, MAX_FLIGHT,
                new ProjectileTrajectory.Environment() {
                    @Override
                    public boolean loaded(Vec3 position) {
                        return true;
                    }

                    @Override
                    public boolean water(Vec3 position) {
                        return false;
                    }

                    @Override
                    public ProjectileTrajectory.Impact collide(Vec3 from, Vec3 to, int tick) {
                        if (to.x < distance) return null;
                        double fraction = (distance - from.x) / (to.x - from.x);
                        return new ProjectileTrajectory.Impact(from.lerp(to, fraction), new Vec3(0, 1, 0));
                    }
                });
    }

    private static double speed(int ticks) {
        return BowItem.getPowerForTime(ticks) * BOW_POWER;
    }

    private static Vec3 start(LocalPlayer player) {
        return player.getEyePosition().add(0, -WeaponShot.SPAWN_OFFSET, 0);
    }

    private boolean ready(Minecraft client) {
        var player = client.player;
        return Gameplay.canAct(client) && client.gameMode != null && !player.isPassenger()
                && player.containerMenu == player.inventoryMenu;
    }

    private void stop(Minecraft client) {
        var player = client.player;
        if (holdingUse) client.options.keyUse.setDown(false);
        holdingUse = false;
        boolean released = false;
        if (drawing && player != null && client.gameMode != null && player.isUsingItem()) {
            client.gameMode.releaseUsingItem(player);
            nextShot = player.tickCount + interaction.placeDelayTicks();
            if (shot != null && shot.spot() != null) {
                armedSpot = shot.spot();
                armedUntil = player.tickCount + shot.flight() - 1;
            }
            releasedAt = player.tickCount;
            released = true;
        }
        drawing = false;
        switching = false;
        shot = null;
        if (restorePending) return;
        if (released && bowSlot >= 0) {
            restorePending = true;
            releaseBoundary = boundary;
            return;
        }
        restoreSlot(client);
    }

    private void restoreSlot(Minecraft client) {
        var player = client.player;
        if (bowSlot >= 0) {
            boolean restore = player != null && SlotSelection.owns(this, player.getInventory())
                    && player.getInventory().getSelectedSlot() == bowSlot;
            SlotSelection.complete(this, restore ? originalSlot : -1);
            if (restore) SlotSelection.syncCarriedItem(client);
        }
        restorePending = false;
        bowSlot = originalSlot = -1;
    }

    private BlockPos only(BlockPos pos) {
        return raycast.target() ? pos : null;
    }

    private static Item rail(Minecraft client) {
        for (Item rail : RAILS) {
            if (InventoryUtils.findHotbar(client.player.getInventory(), rail) >= 0) return rail;
        }
        return null;
    }

    private static boolean flameBow(Minecraft client, ItemStack stack) {
        if (!(stack.getItem() instanceof BowItem)) return false;
        var flame = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.FLAME);
        return flame.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0).orElse(false);
    }

    private static boolean arrowInFlight(Minecraft client) {
        var player = client.player;
        return !client.level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(48),
                arrow -> arrow.getOwner() == player && arrow.isOnFire() && arrow.isAlive()
                        && !((ArrowTrajectoryAccessor) arrow).paradox$isInGround()).isEmpty();
    }

    @Override
    protected void onDisable() {
        stop(Minecraft.getInstance());
        interaction.disable();
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 221)
    private void onPreTick(PreTickEvent event) {
        if (restorePending && boundary != releaseBoundary) restoreSlot(event.client());
        interaction.tickSwap(event.client());
    }

    @EventHandler(priority = 41)
    private void onManualUse(ManualUseEvent event) {
        if (!interaction.allowManualUse(event.client())) event.cancel();
    }

    @EventHandler(priority = 41)
    private void onTickBoundary(TickBoundaryEvent event) {
        boundary++;
        interaction.afterTickBoundary();
    }
}
