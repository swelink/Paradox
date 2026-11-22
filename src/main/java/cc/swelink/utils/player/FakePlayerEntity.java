package cc.swelink.utils.player;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;

public final class FakePlayerEntity extends RemotePlayer {
    public FakePlayerEntity(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Override
    public PlayerSkin getSkin() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.getSkin() : super.getSkin();
    }

    @Override
    public void aiStep() {
        Vec3 velocity = getDeltaMovement().add(0, -0.08, 0);
        move(MoverType.SELF, velocity);
        float friction = onGround() ? 0.6f * 0.91f : 0.91f;
        double y = onGround() && velocity.y < 0 ? 0 : velocity.y * 0.98;
        setDeltaMovement(velocity.x * friction, y, velocity.z * friction);
    }
}
