package cc.swelink.utils.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class RotationManager {
    private static final int LINGER_TICKS = 2;
    private int idleTicks;
    private Request pendingRotation;
    private Request pendingCameraRotation;
    private int appliedPriority = Integer.MIN_VALUE;
    private LocalPlayer player;
    private Rotation currentRotation;
    private Rotation serverRotation;
    private Rotation previousServerRotation;
    private Rotation forcedRotation;
    private Rotation usedRotation;
    private boolean useAfterMovement;
    private boolean movedThisTick;
    private Rotation interactionRotation;
    private MoveFix interactionCorrection = MoveFix.OFF;
    private MoveFix correction = MoveFix.OFF;
    private float returnSpeed = 15;

    private record Request(Rotation target, float speed, int priority, RotationMode mode,
                           MoveFix correction, boolean exact) {
    }

    public void beginTick() {
        forcedRotation = null;
        pendingRotation = null;
        pendingCameraRotation = null;
        appliedPriority = Integer.MIN_VALUE;
    }

    public void beginInputTick() {
        releaseLocalRotation();
    }

    public void releaseLocalRotation() {
        interactionRotation = null;
        interactionCorrection = MoveFix.OFF;
    }

    public void holdLocalRotation(Minecraft client) {
        holdLocalRotation(client, true);
    }

    public void holdLocalRotation(Minecraft client, boolean movefix) {
        holdLocalRotation(client, movefix ? MoveFix.SILENT : MoveFix.OFF);
    }

    public void holdLocalRotation(Minecraft client, MoveFix movementCorrection) {
        if (movementCorrection == null) {
            return;
        }
        if (client.player != null) {
            interactionRotation = new Rotation(client.player.getYRot(), client.player.getXRot());
            interactionCorrection = movementCorrection;
        }
    }

    public void request(Rotation target, float maxDegreesPerTick, int priority) {
        request(target, maxDegreesPerTick, priority, RotationMode.CHANGE_LOOK, MoveFix.OFF);
    }

    public void requestSilent(Rotation target, float maxDegreesPerTick, int priority) {
        requestSilent(target, maxDegreesPerTick, priority, true);
    }

    public void requestSilent(Rotation target, float maxDegreesPerTick, int priority, boolean movefix) {
        requestSilent(target, maxDegreesPerTick, priority,
                movefix ? MoveFix.SILENT : MoveFix.OFF);
    }

    public void requestSilent(Rotation target, float maxDegreesPerTick, int priority, MoveFix movementCorrection) {
        request(target, maxDegreesPerTick, priority, RotationMode.SILENT, movementCorrection);
    }

    public void requestExact(Rotation target, int priority, MoveFix movementCorrection) {
        request(target, 360, priority, RotationMode.SILENT, movementCorrection, true);
    }

    public void request(Rotation target, float speed, int priority, RotationMode mode, MoveFix movementCorrection) {
        request(target, speed, priority, mode, movementCorrection, false);
    }

    private void request(Rotation target, float speed, int priority, RotationMode mode, MoveFix movementCorrection,
                         boolean exact) {
        if (target == null || mode == null || movementCorrection == null || !(speed > 0) || !Float.isFinite(speed)) {
            return;
        }
        if (mode == RotationMode.CHANGE_LOOK) {
            if (pendingCameraRotation == null || priority > pendingCameraRotation.priority) {
                pendingCameraRotation = new Request(target, speed, priority, mode, movementCorrection, exact);
            }
        } else if (pendingRotation == null || priority > pendingRotation.priority) {
            pendingRotation = new Request(target, speed, priority, mode, movementCorrection, exact);
        }
    }

    public void apply(Minecraft client) {
        if (client.player == null || client.level == null) {
            reset();
            return;
        }
        if (player != client.player) {
            Request request = pendingRotation;
            Request lookRequest = pendingCameraRotation;
            Rotation held = interactionRotation;
            MoveFix heldCorrection = interactionCorrection;
            reset();
            pendingRotation = request;
            pendingCameraRotation = lookRequest;
            interactionRotation = held;
            interactionCorrection = heldCorrection;
            player = client.player;
        }
        Rotation view = new Rotation(player.getYRot(), player.getXRot());
        if (serverRotation == null) {
            serverRotation = view;
        }
        Request request = pendingRotation;
        Request lookRequest = pendingCameraRotation;
        pendingRotation = null;
        pendingCameraRotation = null;
        if (request != null && lookRequest != null) {
            if (lookRequest.priority >= request.priority) {
                request = null;
            } else {
                lookRequest = null;
            }
        }
        int priority = request != null ? request.priority
                : lookRequest != null ? lookRequest.priority : Integer.MIN_VALUE;
        if (priority < appliedPriority) {
            return;
        }
        appliedPriority = priority;
        if (lookRequest != null) {
            Rotation next = RotationUtils.stepTowards(view, lookRequest.target, lookRequest.speed);
            player.setYRot(next.yaw());
            player.setXRot(next.pitch());
            view = next;
        }
        if (request != null) {
            Rotation from = currentRotation == null ? serverRotation : currentRotation;
            currentRotation = request.exact ? request.target
                    : RotationUtils.snapToSensitivity(RotationUtils.stepTowards(from, request.target, request.speed), from);
            correction = request.correction;
            returnSpeed = request.speed;
            idleTicks = 0;
        } else if (lookRequest != null) {
            currentRotation = null;
            correction = MoveFix.OFF;
        } else if (currentRotation != null && ++idleTicks > LINGER_TICKS) {
            Rotation next = RotationUtils.stepTowards(currentRotation, view, returnSpeed);
            if (Math.abs(RotationUtils.yawDifference(next.yaw(), view.yaw())) < 0.5f
                    && Math.abs(next.pitch() - view.pitch()) < 0.5f) {
                currentRotation = null;
                correction = MoveFix.OFF;
            } else {
                currentRotation = RotationUtils.snapToSensitivity(next, currentRotation);
            }
        }
    }

    public boolean isSilent() {
        return currentRotation != null || interactionRotation != null;
    }

    public boolean hasHigherPriority(int priority) {
        return appliedPriority > priority;
    }

    public void forcePacketRotation(Rotation rotation) {
        forcedRotation = rotation;
    }

    public void lockUseRotation(Rotation rotation) {
        usedRotation = rotation;
        useAfterMovement = true;
    }

    public Rotation usedRotation() {
        return usedRotation;
    }

    public void movementSent() {
        useAfterMovement = false;
        movedThisTick = true;
    }

    public void tickEndSent() {
        if (!movedThisTick || !useAfterMovement) usedRotation = null;
        movedThisTick = false;
    }

    public Rotation packetRotation() {
        if (usedRotation != null) {
            return usedRotation;
        }
        if (forcedRotation != null) {
            return forcedRotation;
        }
        if (interactionRotation != null) {
            return serverRotation == null ? interactionRotation : interactionRotation.nearestTo(serverRotation);
        }
        return currentRotation == null ? null : serverRotation == null ? currentRotation : currentRotation.nearestTo(serverRotation);
    }

    public Rotation serverRotation() {
        return serverRotation;
    }

    public Rotation previousServerRotation() {
        return previousServerRotation;
    }

    public float packetYaw(float vanillaYaw) {
        Rotation rotation = packetRotation();
        if (rotation != null) {
            return rotation.yaw();
        }
        return serverRotation == null ? vanillaYaw : serverRotation.yaw() + RotationUtils.yawDifference(serverRotation.yaw(), vanillaYaw);
    }

    public float packetPitch(float vanillaPitch) {
        Rotation rotation = packetRotation();
        return rotation == null ? Math.clamp(vanillaPitch, -90, 90) : rotation.pitch();
    }

    public MoveFix movementCorrection() {
        return interactionRotation == null ? correction : interactionCorrection;
    }

    public float movementYaw(float vanillaYaw) {
        if (interactionRotation != null) {
            return interactionCorrection == MoveFix.OFF ? vanillaYaw : interactionRotation.yaw();
        }
        return currentRotation != null && correction != MoveFix.OFF ? currentRotation.yaw() : vanillaYaw;
    }

    public void recordMovementPacket(float yaw, float pitch) {
        if (serverRotation != null && (serverRotation.yaw() != yaw || serverRotation.pitch() != pitch)) {
            previousServerRotation = serverRotation;
        }
        serverRotation = new Rotation(yaw, pitch);
    }

    public void acceptServerRotation(float yaw, float pitch) {
        appliedPriority = Integer.MIN_VALUE;
        interactionRotation = null;
        interactionCorrection = MoveFix.OFF;
        currentRotation = null;
        pendingRotation = null;
        pendingCameraRotation = null;
        correction = MoveFix.OFF;
        forcedRotation = null;
        serverRotation = new Rotation(yaw, pitch);
        previousServerRotation = serverRotation;
    }

    public void clear() {
        appliedPriority = Integer.MIN_VALUE;
        pendingRotation = null;
        pendingCameraRotation = null;
    }

    public void reset() {
        appliedPriority = Integer.MIN_VALUE;
        interactionRotation = null;
        interactionCorrection = MoveFix.OFF;
        pendingRotation = null;
        pendingCameraRotation = null;
        player = null;
        currentRotation = null;
        serverRotation = null;
        previousServerRotation = null;
        forcedRotation = null;
        correction = MoveFix.OFF;
    }
}
