package cc.swelink.systems.modules.movement.speed.mode;

import cc.swelink.Paradox;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.systems.modules.movement.TargetStrafeModule;
import cc.swelink.systems.modules.movement.speed.SpeedModule;
import cc.swelink.utils.player.InventoryUtils;
import cc.swelink.utils.player.SlotSelection;
import cc.swelink.utils.rotation.MoveFix;
import cc.swelink.utils.rotation.Rotation;
import cc.swelink.utils.rotation.RotationSettings;
import cc.swelink.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.List;

public final class GrimSpeed extends SpeedMode {
    private static final double REACH = 0.199;
    private static final int PRIORITY = -3;
    private final BooleanSetting hop = add(new BooleanSetting("Hop", true));
    private final BooleanSetting strafe = add(new BooleanSetting("Strafe 45", true));
    private final BooleanSetting push = add(new BooleanSetting("Entity push", true));
    private final BooleanSetting boats = add(new BooleanSetting("Boats", false));
    private final IntSetting boatSwitchDelay = add(new IntSetting("Boat switch delay", 0, 0, 20));
    private final IntSetting boatPlaceDelay = add(new IntSetting("Boat place delay", 2, 1, 20));
    private final IntSetting boatSwapBackDelay = add(new IntSetting("Boat swap back delay", 2, 1, 20));
    private final RotationSettings boatRotations = new RotationSettings(setting -> add(setting).visibleAlsoWhen(() -> (push.get() || leniency()) && boats.get()));
    private final BooleanSetting hardLeniency = add(new BooleanSetting("Boat leniency", true));
    private final PercentSetting leniencyAmount = add(new PercentSetting("Leniency", 90));
    private int ticks;
    private int lastHard = Integer.MIN_VALUE / 2;
    private Vec3 extra = Vec3.ZERO;
    private final int[] counted = new int[2];
    private int now, thisTick, settle, lastSide = 1;
    private Vec3 tickStart;
    private Rotation target;
    private boolean turned;
    private Rotation placedRotation;
    private boolean boatAimed;
    private boolean placingBoat;
    private boolean placedCamera;
    private boolean boatRequested;
    private AABB lastBoat;
    private int lastBoatTick;
    private int originalSlot = -1;
    private static final double BOAT_HEIGHT = 0.5625;
    private int switchedAt;
    private boolean switched;

    public GrimSpeed(SpeedModule module) {
        super(module, "Grim");
        boats.visibleWhen(() -> module.selected(this) && (push.get() || leniency()));
        boatSwitchDelay.visibleWhen(() -> module.selected(this) && (push.get() || leniency()) && boats.get());
        boatPlaceDelay.visibleWhen(() -> module.selected(this) && (push.get() || leniency()) && boats.get());
        boatSwapBackDelay.visibleWhen(() -> module.selected(this) && (push.get() || leniency()) && boats.get());
        leniencyAmount.visibleWhen(() -> module.selected(this) && hardLeniency.get());
    }

    private boolean leniency() {
        return hardLeniency.get();
    }

    @Override
    public Input prepareInput(Minecraft client, Input input) {
        var player = client.player;
        turned = false;
        tickStart = null;
        ticks++;
        if (settle > 0) settle--;
        if (leniency() && settle == 0 && hardCollider(player)) lastHard = ticks;
        if (hop.get() && input.forward() && !input.backward()) {
            boolean jump = input.jump() || player.onGround() && !input.shift() && !player.isUsingItem();
            input = new Input(true, false, input.left(), input.right(), jump, input.shift(), true);
        }
        boatAimed = false;
        boatRequested = false;
        if (placedCamera) {
            placedCamera = false;
        } else if (placedRotation != null) {
            target = placedRotation;
            placedRotation = null;
            Paradox.ROTATIONS.requestExact(target, PRIORITY, MoveFix.SILENT);
            Paradox.ROTATIONS.apply(client);
            turned = true;
            if (input.forward() || input.backward() || input.left() || input.right()) {
                input = new Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), input.shift(), true);
            }
        } else {
            Rotation aim = boatAim(client, player);
            if (strafe.get() && settle == 0 && !input.shift() && !player.isUsingItem()) {
                input = strafe(client, player, input, aim);
            }
            if (aim != null && !boatRequested && !Paradox.ROTATIONS.hasHigherPriority(PRIORITY)) {
                requestBoat(client, player, aim, false);
            }
            boatAimed = boatRequested && !Paradox.ROTATIONS.hasHigherPriority(PRIORITY);
        }
        if (push.get() && collision(player) == Team.CollisionRule.ALWAYS) {
            tickStart = player.position();
            now = pushers(player, tickStart, tickStart);
        }
        return input;
    }

    private Input strafe(Minecraft client, LocalPlayer player, Input keys, Rotation aim) {
        int forward = (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0);
        int sideways = (keys.left() ? 1 : 0) - (keys.right() ? 1 : 0);
        if (forward == 0 && sideways == 0 || Paradox.ROTATIONS.hasHigherPriority(PRIORITY)
                || client.options.keyAttack.isDown() || client.options.keyUse.isDown()) return keys;
        double angle = Math.toDegrees(Math.atan2(sideways, forward));
        int side;
        if (keys.jump() && player.onGround()) {
            side = 0;
        } else {
            if (angle > 0.5) lastSide = 1;
            else if (angle < -0.5) lastSide = -1;
            side = lastSide;
        }
        float yaw = (float) (player.getYRot() - angle + 45 * side);
        if (aim != null) {
            requestBoat(client, player, aim, true);
        } else {
            target = new Rotation(yaw, player.getXRot());
            Paradox.ROTATIONS.requestSilent(target, 180, PRIORITY, true);
            Paradox.ROTATIONS.apply(client);
            turned = true;
        }
        return new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), keys.shift(), true);
    }

    private void requestBoat(Minecraft client, LocalPlayer player, Rotation aim, boolean strafing) {
        boatRequested = true;
        if (boatRotations.silent()) {
            target = aim;
            if (strafing) boatRotations.requestGlide(client, target, PRIORITY);
            else boatRotations.request(client, target, PRIORITY);
            turned = true;
        } else {
            boatRotations.request(client, aim, PRIORITY);
        }
        Paradox.ROTATIONS.apply(client);
    }

    private Rotation boatAim(Minecraft client, LocalPlayer player) {
        if (!wantsBoats(client, player) || player.onGround()) return null;
        if (lastBoat != null && player.tickCount + 1 - lastBoatTick < boatPlaceDelay.get()) return null;
        Vec3 velocity = player.getDeltaMovement();
        Vec3 position = player.position().add(velocity);
        Vec3 motion = new Vec3(velocity.x, (velocity.y - 0.08) * 0.98, velocity.z);
        Vec3 eye = position.add(0, player.getEyeHeight(), 0);
        int spawn = spawnTicks(client);
        for (int tick = 1; tick <= 40; tick++) {
            position = position.add(motion);
            motion = new Vec3(motion.x, (motion.y - 0.08) * 0.98, motion.z);
            double ground = groundY(player, position);
            if (Double.isNaN(ground)) continue;
            if (position.y > ground + BOAT_HEIGHT) continue;
            if (tick <= spawn) return null;
            Vec3 spot = new Vec3(position.x, ground, position.z);
            if (eye.distanceTo(spot) > player.blockInteractionRange() - 0.05) return null;
            AABB boat = EntityTypes.OAK_BOAT.getDimensions().makeBoundingBox(spot);
            if (!clearOfPath(player, boat, spawn) || blockedByBoats(player, boat)) return null;
            return RotationUtils.lookAt(eye, spot);
        }
        return null;
    }

    private static double groundY(LocalPlayer player, Vec3 position) {
        var hit = player.level().clip(new ClipContext(position.add(0, 1, 0), position.add(0, -3, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : Double.NaN;
    }

    private static int spawnTicks(Minecraft client) {
        var connection = client.getConnection();
        var info = connection == null || client.player == null ? null : connection.getPlayerInfo(client.player.getUUID());
        int ping = info == null ? 0 : Math.max(info.getLatency(), 0);
        return (ping + 49) / 50 + 2;
    }

    private boolean clearOfPath(LocalPlayer player, AABB boat, int spawn) {
        Vec3 velocity = player.getDeltaMovement();
        AABB body = player.getBoundingBox();
        Vec3 offset = Vec3.ZERO;
        Vec3 motion = velocity;
        boolean counted = false;
        for (int tick = 0; tick <= 40; tick++) {
            AABB at = body.move(offset);
            if (tick <= spawn + 1 && at.intersects(boat)) return false;
            if (tick > spawn && at.inflate(REACH).intersects(boat)) counted = true;
            if (tick > spawn && at.minY < boat.maxY) break;
            offset = offset.add(motion);
            motion = new Vec3(motion.x, (motion.y - 0.08) * 0.98, motion.z);
        }
        return counted;
    }

    private boolean blockedByBoats(LocalPlayer player, AABB boat) {
        if (lastBoat != null && player.tickCount - lastBoatTick <= spawnTicks(Minecraft.getInstance())
                && lastBoat.intersects(boat)) return true;
        return !player.level().getEntities((Entity) null, boat, entity -> entity instanceof AbstractBoat).isEmpty();
    }

    private boolean wantsBoats(Minecraft client, LocalPlayer player) {
        return boats.get() && settle == 0 && (push.get() && collision(player) == Team.CollisionRule.ALWAYS || leniency())
                && !player.isUsingItem() && !client.options.keyAttack.isDown() && !client.options.keyUse.isDown()
                && boatHand(player) != null;
    }

    private static InteractionHand boatHand(LocalPlayer player) {
        if (player.getOffhandItem().getItem() instanceof BoatItem) return InteractionHand.OFF_HAND;
        return InventoryUtils.findHotbar(player.getInventory(), stack -> stack.getItem() instanceof BoatItem) >= 0
                ? InteractionHand.MAIN_HAND : null;
    }

    public void tickInput(Minecraft client) {
        var player = client.player;
        placedRotation = null;
        placedCamera = false;
        boolean aimed = boatAimed;
        boatAimed = false;
        if (originalSlot >= 0 && !switched && player.tickCount - lastBoatTick >= boatSwapBackDelay.get()) restoreSlot(client);
        if (switched && player.tickCount - switchedAt >= boatSwitchDelay.get() + boatSwapBackDelay.get()) restoreSlot(client);
        if (!aimed || client.gameMode == null || client.gui.screen() != null || player.isHandsBusy()
                || !wantsBoats(client, player)) return;
        if (lastBoat != null && player.tickCount - lastBoatTick < boatPlaceDelay.get()) return;
        Rotation sent;
        if (boatRotations.silent()) {
            sent = Paradox.ROTATIONS.serverRotation();
            Rotation packet = Paradox.ROTATIONS.packetRotation();
            if (sent == null || packet == null || packet.yaw() != sent.yaw() || packet.pitch() != sent.pitch()) return;
        } else {
            if (Paradox.ROTATIONS.isSilent()) return;
            sent = new Rotation(player.getYRot(), player.getXRot());
        }
        AABB boat = placement(player, sent);
        if (boat == null) return;
        InteractionHand hand = boatHand(player);
        if (hand == null) return;
        var inventory = player.getInventory();
        if (hand == InteractionHand.MAIN_HAND && !(player.getMainHandItem().getItem() instanceof BoatItem)) {
            if (switched) return;
            int slot = InventoryUtils.findHotbar(inventory, stack -> stack.getItem() instanceof BoatItem);
            if (slot < 0 || !SlotSelection.begin(module, inventory)) return;
            originalSlot = inventory.getSelectedSlot();
            if (!SlotSelection.select(module, inventory, slot)) {
                SlotSelection.complete(module, -1);
                originalSlot = -1;
                return;
            }
            SlotSelection.syncCarriedItem(client);
            switched = true;
            switchedAt = player.tickCount;
            if (boatSwitchDelay.get() > 0) return;
        }
        if (switched && player.tickCount - switchedAt < boatSwitchDelay.get()) return;
        switched = false;
        lastBoat = boat;
        lastBoatTick = player.tickCount;
        if (boatRotations.silent()) placedRotation = sent;
        else placedCamera = true;
        var animation = player.getItemInHand(hand).getInteractAnimation();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        InteractionResult result;
        placingBoat = true;
        player.setYRot(sent.yaw());
        player.setXRot(sent.pitch());
        try {
            result = client.gameMode.useItem(player, hand);
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
            placingBoat = false;
        }
        if (result instanceof InteractionResult.Success success
                && success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            player.swing(hand, animation, false);
        }
    }

    private AABB placement(LocalPlayer player, Rotation rotation) {
        var level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 view = Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
        var hit = level.clip(new ClipContext(eye, eye.add(view.scale(player.blockInteractionRange())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        for (Entity entity : level.getEntities(player, player.getBoundingBox().expandTowards(view.scale(5)).inflate(1),
                EntitySelector.CAN_BE_PICKED)) {
            if (entity.getBoundingBox().inflate(entity.getPickRadius()).contains(eye)) return null;
        }
        AABB boat = EntityTypes.OAK_BOAT.getDimensions().makeBoundingBox(hit.getLocation());
        if (blockedByBoats(player, boat) || !level.noCollision(boat)) return null;
        return clearOfPath(player, boat, spawnTicks(Minecraft.getInstance())) ? boat : null;
    }

    private void restoreSlot(Minecraft client) {
        switched = false;
        if (originalSlot < 0) return;
        var player = client.player;
        boolean owned = player != null && SlotSelection.owns(module, player.getInventory());
        SlotSelection.complete(module, owned ? originalSlot : -1);
        if (owned) SlotSelection.syncCarriedItem(client);
        originalSlot = -1;
    }

    public void releaseBoats(Minecraft client) {
        placedRotation = null;
        placedCamera = false;
        boatAimed = false;
        lastBoat = null;
        restoreSlot(client);
    }

    @Override
    public Vec3 movement(Minecraft client, Vec3 direction, Vec3 original) {
        var player = client.player;
        extra = Vec3.ZERO;
        if (player.isUsingItem()) return original;
        Vec3 pushed = pushed(client, player, direction, original);
        if (!lenient(player)) return pushed;
        extra = horizontal(client, direction, 1.2 * leniencyAmount.get() / 100.0 - 0.02);
        return pushed.add(extra);
    }

    public Vec3 velocity(Vec3 movement) {
        return movement.subtract(extra);
    }

    private boolean lenient(LocalPlayer player) {
        return leniency() && settle == 0 && !player.isPassenger() && ticks - lastHard <= 2;
    }

    private static boolean hardCollider(LocalPlayer player) {
        Entity vehicle = player.getVehicle();
        return !player.level().getEntities(player, player.getBoundingBox().inflate(0.95), entity -> entity != vehicle
                && entity.tickCount > 1 && !entity.hasPassenger(player)
                && (entity instanceof AbstractBoat || entity instanceof Shulker || entity instanceof HappyGhast)).isEmpty();
    }

    private Vec3 pushed(Minecraft client, LocalPlayer player, Vec3 direction, Vec3 original) {
        if (tickStart == null) return original;
        int pushers = settle > 0 ? 0 : Math.max(now, Math.max(counted[0], counted[1]));
        if (pushers == 0) return original;
        double room = 0.08 * pushers - 0.004;
        Vec3 planned = Paradox.MODULES.get(TargetStrafeModule.class).plannedPush(player);
        if (planned != null) return original.add(planned.x * room, 0, planned.z * room);
        Vec3 push = horizontal(client, direction, 1);
        return original.add(push.scale(room / Math.max(Math.abs(push.x), Math.abs(push.z))));
    }

    public record PushWindow(double room, boolean available, boolean lastTick) {
    }

    public PushWindow pushWindow(LocalPlayer player) {
        if (tickStart == null || settle > 0 || player.isUsingItem()) return null;
        int pushers = Math.max(now, Math.max(counted[0], counted[1]));
        return new PushWindow(0.08 * Math.max(1, pushers) - 0.004, pushers > 0, counted[0] > 0);
    }

    public List<AABB> contactBoxes(Entity entity) {
        return possibleBoxes(entity);
    }

    public void idle() {
        turned = false;
    }

    public void tick() {
        if (turned && !Paradox.ROTATIONS.hasHigherPriority(PRIORITY)) {
            Paradox.ROTATIONS.requestSilent(target, 180, PRIORITY, true);
        }
    }

    public boolean noPush() {
        return push.get();
    }

    public void afterMove(LocalPlayer player) {
        if (tickStart == null) return;
        thisTick = pushers(player, tickStart, player.position());
        tickStart = null;
    }

    public void sentPosition() {
        counted[1] = counted[0];
        counted[0] = Math.max(now, thisTick);
        now = thisTick = 0;
    }

    public void teleported() {
        counted[0] = counted[1] = 0;
        lastHard = Integer.MIN_VALUE / 2;
        settle = 3;
    }

    public boolean usesLocalTarget() {
        return turned && !Paradox.ROTATIONS.hasHigherPriority(PRIORITY);
    }

    public void beforeInteraction(Minecraft client) {
        if (!placingBoat && usesLocalTarget()) Paradox.ROTATIONS.holdLocalRotation(client, true);
    }

    public void refreshLocalTarget(Minecraft client) {
        if (!usesLocalTarget()) return;
        client.hitResult = client.player.raycastHitResult(1, client.player);
        client.crosshairPickEntity = client.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
    }

    private int pushers(LocalPlayer player, Vec3 from, Vec3 to) {
        if (collision(player) != Team.CollisionRule.ALWAYS) return 0;
        AABB path = box(from).minmax(box(to)).inflate(REACH);
        int count = 0;
        for (Entity entity : player.level().getEntities(player, path.inflate(4), GrimSpeed::pushable)) {
            if (possibleBoxes(entity).stream().allMatch(path::intersects)) count++;
        }
        return count;
    }

    private static List<AABB> possibleBoxes(Entity entity) {
        AABB box = entity.getBoundingBox();
        Vec3 position = entity.position();
        List<AABB> boxes = new ArrayList<>();
        boxes.add(box);
        boxes.add(box.move(entity.oldPosition().subtract(position)));
        var interpolation = entity.getInterpolation();
        if (interpolation != null && interpolation.hasActiveInterpolation() && interpolation.target() != null) {
            boxes.add(box.move(interpolation.target().position().subtract(position)));
        }
        return boxes;
    }

    static boolean pushable(Entity entity) {
        boolean kind = entity instanceof LivingEntity
                ? !(entity instanceof ArmorStand) && !(entity instanceof Bat) && !(entity instanceof Parrot)
                : entity instanceof AbstractBoat || entity instanceof AbstractMinecart;
        return kind && !entity.isSpectator() && collision(entity) == Team.CollisionRule.ALWAYS;
    }

    private static AABB box(Vec3 position) {
        return new AABB(position.x - 0.3, position.y, position.z - 0.3, position.x + 0.3, position.y + 1.8, position.z + 0.3);
    }

    private static Team.CollisionRule collision(Entity entity) {
        Team team = entity.getTeam();
        return team == null ? Team.CollisionRule.ALWAYS : team.getCollisionRule();
    }
}
