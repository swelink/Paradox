package cc.swelink.systems.modules.player;

import cc.swelink.events.AttackEvent;
import cc.swelink.events.EventHandler;
import cc.swelink.events.IncomingPacketEvent;
import cc.swelink.mixin.ConnectionLagAccess;
import cc.swelink.mixin.EntityEventPacketAccessor;
import cc.swelink.mixin.MinecraftRightClickAccess;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class OptimizerModule extends Module {
    private static final int LOW_DELAY = 0x10;
    private static final byte USE_ITEM_COMPLETE = 9;
    private final BooleanSetting socket = addSetting(new BooleanSetting("Low delay socket", true));
    private final BooleanSetting players = addSetting(new BooleanSetting("Fast player positions", true));
    private final BooleanSetting crystals = addSetting(new BooleanSetting("Crystals", true));
    private final BooleanSetting anchors = addSetting(new BooleanSetting("Anchors", true));
    private final BooleanSetting eatDelay = addSetting(new BooleanSetting("No eat delay", true));
    private final BooleanSetting eatFinish = addSetting(new BooleanSetting("Instant eat finish", false));
    private final BooleanSetting pingCompensation = addSetting(new BooleanSetting("Ping compensation", false));
    private Channel tuned;
    private int finishedSince = -1;
    private int confirmDeadline = -1;

    public OptimizerModule() {
        super("Optimizer", "Cut network, interpolation, crystal, anchor and eating delay without changing what the server sees.",
                Category.PLAYER);
    }

    @Override
    public void tick(Minecraft client) {
        var connection = client.getConnection();
        Channel channel = connection == null ? null : ((ConnectionLagAccess) connection.getConnection()).paradox$channel();
        if (!socket.get() || channel == null || !channel.isOpen()) {
            if (tuned != null && tuned.isOpen()) configure(tuned, false);
            tuned = null;
            return;
        }
        if (channel != tuned) {
            if (tuned != null && tuned.isOpen()) configure(tuned, false);
            tuned = channel;
        }
        configure(channel, true);
    }

    @Override
    public void tickInput(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;
        var input = (MinecraftRightClickAccess) client;
        if (eatFinish.get()) predictFinish(client, player);
        if (confirmDeadline >= 0) {
            if (player.tickCount <= confirmDeadline) {
                input.paradox$setRightClickDelay(Math.max(input.paradox$rightClickDelay(), 1));
                return;
            }
            confirmDeadline = -1;
        }
        if (eatDelay.get() && !player.isUsingItem() && client.options.keyUse.isDown()
                && (consumable(player.getMainHandItem()) || consumable(player.getOffhandItem()))) {
            input.paradox$setRightClickDelay(0);
        }
    }

    @Override
    protected void onDisable() {
        if (tuned != null && tuned.isOpen()) configure(tuned, false);
        tuned = null;
        finishedSince = -1;
        confirmDeadline = -1;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 50)
    private void onAttack(AttackEvent event) {
        breakCrystal(event.client(), event.entityId());
    }

    @EventHandler(priority = 50)
    private void onIncomingPacket(IncomingPacketEvent event) {
        var client = Minecraft.getInstance();
        if (event.packet() instanceof ClientboundEntityEventPacket packet && client.player != null
                && packet.getEventId() == USE_ITEM_COMPLETE
                && ((EntityEventPacketAccessor) packet).paradox$entityId() == client.player.getId()) {
            confirmDeadline = -1;
        }
    }

    public boolean pingCompensation() {
        return isEnabled() && pingCompensation.get();
    }

    public int interpolationSteps(Entity entity, int steps) {
        return isEnabled() && players.get() && entity instanceof RemotePlayer ? Math.min(steps, 1) : steps;
    }

    public void anchorUsed(LocalPlayer player, InteractionHand hand, BlockPos pos, BlockState clicked) {
        if (!isEnabled() || !anchors.get() || hand != InteractionHand.MAIN_HAND || player.isSpectator()) return;
        if (!(clicked.getBlock() instanceof RespawnAnchorBlock) || clicked.getValue(RespawnAnchorBlock.CHARGE) == 0) return;
        boolean holding = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        if (player.isSecondaryUseActive() && holding) return;
        var level = player.level();
        if (level.getBlockState(pos) != clicked) return;
        if (level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos)) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void breakCrystal(Minecraft client, int entityId) {
        LocalPlayer player = client.player;
        if (!isEnabled() || !crystals.get() || player == null || client.level == null || player.isSpectator()) return;
        if (!(client.level.getEntity(entityId) instanceof EndCrystal crystal) || crystal.isRemoved() || crystal.isInvulnerable()) {
            return;
        }
        if (player.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 0 && !player.getMainHandItem().isEnchanted()) return;
        client.level.removeEntity(crystal.getId(), Entity.RemovalReason.KILLED);
    }

    private void predictFinish(Minecraft client, LocalPlayer player) {
        ItemStack stack = player.getUseItem();
        if (!player.isUsingItem() || !consumable(stack) || stack.useOnRelease() || player.getUseItemRemainingTicks() > 0) {
            finishedSince = -1;
            return;
        }
        long latency = latency(client, player);
        if (latency < 0) return;
        if (finishedSince < 0) finishedSince = player.tickCount;
        int roundTrip = (int) Math.ceil(latency / 50.0);
        if (player.tickCount - finishedSince < Math.max(0, roundTrip - 1)) return;
        player.stopUsingItem();
        finishedSince = -1;
        confirmDeadline = player.tickCount + roundTrip + 10;
    }

    private static long latency(Minecraft client, LocalPlayer player) {
        if (client.hasSingleplayerServer()) return 0;
        var connection = client.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    private static boolean consumable(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.CONSUMABLE);
    }

    @SuppressWarnings("unchecked")
    private static void configure(Channel channel, boolean enabled) {
        channel.eventLoop().execute(() -> {
            var config = channel.config();
            try {
                config.setOption(ChannelOption.TCP_NODELAY, true);
                config.setOption(ChannelOption.IP_TOS, enabled ? LOW_DELAY : 0);
                for (ChannelOption<?> option : config.getOptions().keySet()) {
                    if (option.name().endsWith("TCP_QUICKACK")) config.setOption((ChannelOption<Boolean>) option, enabled);
                }
            } catch (ChannelException ignored) {
            }
        });
    }
}
