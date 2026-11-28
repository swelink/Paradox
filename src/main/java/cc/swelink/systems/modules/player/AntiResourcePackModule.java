package cc.swelink.systems.modules.player;

import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

public final class AntiResourcePackModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Spoof", "Spoof", "Decline"));

    public AntiResourcePackModule() {
        super("AntiPack", "Skips server resource packs", Category.CLIENT);
    }

    public boolean skip(Connection connection, ClientboundResourcePackPushPacket packet) {
        if (!isEnabled()) return false;
        if (mode.get().equals("Decline")) {
            connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.DECLINED));
            return true;
        }
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.DOWNLOADED));
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        return true;
    }
}
