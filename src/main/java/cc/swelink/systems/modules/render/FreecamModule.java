package cc.swelink.systems.modules.render;

import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class FreecamModule extends Module {
    private final IntSetting speed = addSetting(new IntSetting("Speed", 10, 1, 50) {
        @Override
        protected String formatValue(int value) {
            return String.format(Locale.ROOT, "%d b/s", value);
        }
    });
    private final BooleanSetting scrollSpeed = addSetting(new BooleanSetting("Scroll speed", true));
    private final BooleanSetting toggleOnDamage = addSetting(new BooleanSetting("Toggle on damage", false));
    private final BooleanSetting toggleOnDeath = addSetting(new BooleanSetting("Toggle on death", true));
    private LocalPlayer player;
    private Vec3 position;
    private Vec3 previous;
    private float yaw;
    private float pitch;
    private float health;

    public FreecamModule() {
        super("Freecam", "Fly the camera freely", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            setEnabled(false);
            return;
        }
        player = client.player;
        position = client.gameRenderer.mainCamera().position();
        previous = position;
        yaw = player.getYRot();
        pitch = player.getXRot();
        health = player.getHealth();
    }

    @Override
    protected void onDisable() {
        player = null;
        position = null;
        previous = null;
    }

    @Override
    public void tick(Minecraft client) {
        if (position == null) return;
        if (client.player != player || client.level == null
                || toggleOnDeath.get() && !player.isAlive()
                || toggleOnDamage.get() && player.getHealth() < health) {
            setEnabled(false);
            return;
        }
        health = player.getHealth();
        previous = position;
        var options = client.options;
        boolean typing = client.gui.screen() != null;
        double forward = typing ? 0 : (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0);
        double sideways = typing ? 0 : (options.keyLeft.isDown() ? 1 : 0) - (options.keyRight.isDown() ? 1 : 0);
        double vertical = typing ? 0 : (options.keyJump.isDown() ? 1 : 0) - (options.keyShift.isDown() ? 1 : 0);
        double length = Math.sqrt(forward * forward + sideways * sideways);
        if (length > 1) {
            forward /= length;
            sideways /= length;
        }
        double step = speed.get() / 20.0 * (!typing && options.keySprint.isDown() ? 2 : 1);
        double sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
        double cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
        position = position.add((sideways * cos - forward * sin) * step, vertical * step,
                (forward * cos + sideways * sin) * step);
    }

    public boolean active() {
        var client = Minecraft.getInstance();
        return isEnabled() && position != null && player == client.player && client.level != null;
    }

    public boolean turn(double horizontal, double vertical) {
        if (!active()) return false;
        yaw += (float) (horizontal * 0.15);
        pitch = Math.clamp(pitch + (float) (vertical * 0.15), -90, 90);
        return true;
    }

    public boolean scroll(double amount) {
        if (!active() || !scrollSpeed.get() || amount == 0) return false;
        speed.setValue(speed.get() + (amount > 0 ? 1 : -1));
        return true;
    }

    public Vec3 position(float partialTick) {
        return previous.lerp(position, partialTick);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    @EventHandler(priority = 1000)
    private void onInput(InputEvent event) {
        if (active()) event.input(Input.EMPTY);
    }
}
