package cc.swelink.utils.rotation;

import net.minecraft.world.entity.player.Input;

public final class SilentMoveFix {
    private static final double STEP = Math.PI / 4;
    private static final double SPRINT_SPEED = 1.3;
    private static final double MAX_ERROR = 1.5;

    private static double lateralError;
    private static double lastAngle = Double.NaN;

    private SilentMoveFix() {
    }

    public static void reset() {
        lateralError = 0;
        lastAngle = Double.NaN;
    }

    public static Input correct(Input input, float cameraYaw, float movementYaw, boolean sprinting) {
        int forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        int sideways = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        if (forward == 0 && sideways == 0) {
            reset();
            return input;
        }
        double offset = Math.toRadians(RotationUtils.yawDifference(movementYaw, cameraYaw));
        double angle = Math.atan2(sideways, forward) - offset;
        if (!Double.isNaN(lastAngle) && Math.abs(wrap(angle - lastAngle)) > STEP) {
            lateralError = 0;
        }
        lastAngle = angle;

        int low = (int) Math.floor(angle / STEP);
        double lowError = lateral(low, angle, sprinting, input.sprint());
        double highError = lateral(low + 1, angle, sprinting, input.sprint());
        int chosen;
        double error;
        if (Math.abs(lateralError + lowError) <= Math.abs(lateralError + highError)) {
            chosen = low;
            error = lowError;
        } else {
            chosen = low + 1;
            error = highError;
        }
        lateralError = Math.clamp(lateralError + error, -MAX_ERROR, MAX_ERROR);

        double direction = chosen * STEP;
        int correctedForward = (int) Math.round(Math.cos(direction));
        int correctedSideways = (int) Math.round(Math.sin(direction));
        return new Input(correctedForward > 0, correctedForward < 0, correctedSideways > 0, correctedSideways < 0,
                input.jump(), input.shift(), input.sprint() && correctedForward > 0);
    }

    private static double lateral(int index, double angle, boolean sprinting, boolean sprintKey) {
        double direction = index * STEP;
        boolean forward = Math.round(Math.cos(direction)) > 0;
        double speed = forward && (sprinting || sprintKey) ? SPRINT_SPEED : 1;
        return speed * Math.sin(direction - angle);
    }

    private static double wrap(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }
}
