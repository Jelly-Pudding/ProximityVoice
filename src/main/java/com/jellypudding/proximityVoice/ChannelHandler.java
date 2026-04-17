package com.jellypudding.proximityVoice;

import com.jellypudding.proximityVoice.crypto.VoiceSecret;
import com.jellypudding.proximityVoice.network.VoiceServer;
import com.jellypudding.proximityVoice.protocol.PacketBuffer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

public class ChannelHandler implements PluginMessageListener {

    public static final String CH_REQUEST_SECRET = "voicechat:request_secret";
    public static final String CH_SECRET         = "voicechat:secret";
    public static final String CH_UPDATE_STATE   = "voicechat:update_state";
    public static final String CH_PLAYER_STATE   = "voicechat:state";

    // Ordinal 0 in SVC's ServerConfig.Codec enum (VOIP)
    private static final byte CODEC_VOIP = 0;

    private final ProximityVoice plugin;
    private final VoiceServer voiceServer;
    private final Logger log;

    public ChannelHandler(ProximityVoice plugin, VoiceServer voiceServer) {
        this.plugin = plugin;
        this.voiceServer = voiceServer;
        this.log = plugin.getLogger();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        switch (channel) {
            case CH_REQUEST_SECRET -> handleRequestSecret(player, data);
            case CH_UPDATE_STATE   -> handleUpdateState(player, data);
        }
    }

    private void handleRequestSecret(Player player, byte[] data) {
        try {
            PacketBuffer buf = new PacketBuffer(data);
            int clientVersion = buf.readInt();
            int expectedVersion = plugin.getConfig().getInt("compatibility-version", 20);

            if (clientVersion != expectedVersion) {
                log.warning(player.getName() + " voice chat compat version "
                        + clientVersion + " (expected " + expectedVersion + ") - proceeding anyway");
            }

            VoiceSecret secret = voiceServer.registerSecret(player.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    sendSecret(player, secret);
                } catch (Exception e) {
                    log.warning("Failed to send secret to " + player.getName() + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warning("Failed to handle secret request from " + player.getName() + ": " + e.getMessage());
        }
    }

    private void sendSecret(Player player, VoiceSecret secret) throws Exception {
        int udpPort   = plugin.getConfig().getInt("voice-port", 24454);
        double distance = plugin.getConfig().getDouble("voice-distance", 48.0);
        int keepAlive = plugin.getConfig().getInt("keep-alive", 3000);
        int mtuSize   = plugin.getConfig().getInt("mtu-size", 1024);

        PacketBuffer buf = new PacketBuffer();
        buf.writeBytes(secret.getBytes()); // 16 raw bytes, no length prefix
        buf.writeInt(udpPort);
        buf.writeUUID(player.getUniqueId());
        buf.writeByte(CODEC_VOIP);
        buf.writeInt(mtuSize);
        buf.writeDouble(distance);
        buf.writeInt(keepAlive);
        buf.writeBoolean(false); // groups disabled
        buf.writeString("");     // voiceHost empty = use server IP
        buf.writeBoolean(false); // recording disabled

        send(player, CH_SECRET, buf.toBytes());
        log.info("Sent voice secret to " + player.getName());
    }

    private void handleUpdateState(Player player, byte[] data) {
        try {
            boolean disabled = new PacketBuffer(data).readBoolean();
            voiceServer.updatePlayerState(player.getUniqueId(), disabled);
            broadcastPlayerState(player, disabled, false);
        } catch (Exception e) {
            log.warning("Failed to handle state update from " + player.getName() + ": " + e.getMessage());
        }
    }

    public void broadcastPlayerState(Player player, boolean disabled, boolean disconnected) {
        try {
            byte[] packet = buildStatePacket(player.getUniqueId(), player.getName(), disabled, disconnected);
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                try {
                    send(online, CH_PLAYER_STATE, packet);
                } catch (Exception e) {
                    log.fine("Failed to send state to " + online.getName());
                }
            }
        } catch (IOException e) {
            log.warning("Failed to build state packet for " + player.getName());
        }
    }

    public void sendAllStatesTo(Player target) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.equals(target)) continue;
            PlayerVoiceState state = voiceServer.getState(online.getUniqueId());
            if (state == null) continue;
            try {
                byte[] packet = buildStatePacket(state.getUuid(), state.getName(),
                        state.isDisabled(), !state.isConnected());
                send(target, CH_PLAYER_STATE, packet);
            } catch (Exception e) {
                log.fine("Failed to send state of " + online.getName() + " to " + target.getName());
            }
        }
    }

    private byte[] buildStatePacket(UUID uuid, String name, boolean disabled, boolean disconnected) throws IOException {
        PacketBuffer buf = new PacketBuffer();
        buf.writeBoolean(disabled);
        buf.writeBoolean(disconnected);
        buf.writeUUID(uuid);
        buf.writeString(name);
        buf.writeBoolean(false); // no group
        return buf.toBytes();
    }

    private void send(Player player, String channel, byte[] data) {
        player.sendPluginMessage(plugin, channel, data);
    }
}
