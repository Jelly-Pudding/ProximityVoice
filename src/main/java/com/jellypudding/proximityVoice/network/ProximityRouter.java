package com.jellypudding.proximityVoice.network;

import com.jellypudding.proximityVoice.PlayerVoiceState;
import com.jellypudding.proximityVoice.protocol.PacketBuffer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

public class ProximityRouter {

    private static final byte TYPE_PLAYER_SOUND = 0x02;
    private static final byte FLAG_WHISPER = 0x01;

    private final VoiceServer voiceServer;
    private final DatagramSocket socket;
    private final ExecutorService packetSender;
    private final Logger log;
    private volatile double voiceDistance;

    public ProximityRouter(VoiceServer voiceServer, DatagramSocket socket,
                           ExecutorService packetSender, double voiceDistance, Logger log) {
        this.voiceServer = voiceServer;
        this.socket = socket;
        this.packetSender = packetSender;
        this.voiceDistance = voiceDistance;
        this.log = log;
    }

    public void setVoiceDistance(double distance) { this.voiceDistance = distance; }

    public void route(UUID senderUuid, byte[] opusData, long sequenceNumber, boolean whispering) {
        Player sender = voiceServer.getPlugin().getServer().getPlayer(senderUuid);
        if (sender == null) return;

        PlayerVoiceState senderState = voiceServer.getState(senderUuid);
        if (senderState == null || senderState.isSilent()) return;

        float distance = (float) (whispering
                ? voiceServer.getPlugin().getConfig().getDouble("whisper-distance", 6.0)
                : voiceDistance);

        Location senderLoc = sender.getLocation();
        double distanceSquared = (double) distance * distance;
        List<ClientConnection> recipients = new ArrayList<>();

        for (Player receiver : voiceServer.getPlugin().getServer().getOnlinePlayers()) {
            if (receiver.getUniqueId().equals(senderUuid)) continue;
            if (!receiver.getWorld().equals(sender.getWorld())) continue;
            if (receiver.getLocation().distanceSquared(senderLoc) > distanceSquared) continue;

            ClientConnection conn = voiceServer.getConnection(receiver.getUniqueId());
            if (conn == null) continue;

            PlayerVoiceState receiverState = voiceServer.getState(receiver.getUniqueId());
            if (receiverState != null && receiverState.isSilent()) continue;

            recipients.add(conn);
        }

        if (recipients.isEmpty()) return;

        // Hand off to background thread: encryption and socket.send() stay off the main tick.
        packetSender.submit(() -> {
            for (ClientConnection conn : recipients) {
                sendPlayerSoundPacket(conn, senderUuid, opusData, sequenceNumber, whispering, distance);
            }
        });
    }

    private void sendPlayerSoundPacket(ClientConnection conn, UUID senderUuid,
                                       byte[] opusData, long sequenceNumber,
                                       boolean whispering, float distance) {
        try {
            PacketBuffer payload = new PacketBuffer();
            payload.writeByte(TYPE_PLAYER_SOUND);
            payload.writeUUID(senderUuid); // channelId == sender for proximity
            payload.writeUUID(senderUuid); // sender
            payload.writeByteArray(opusData);
            payload.writeLong(sequenceNumber);
            payload.writeFloat(distance);
            payload.writeByte(whispering ? FLAG_WHISPER : 0);

            byte[] encrypted = conn.getSecret().encrypt(payload.toBytes());

            PacketBuffer wire = new PacketBuffer();
            wire.writeByte(0xFF);
            wire.writeByteArray(encrypted);

            byte[] wireBytes = wire.toBytes();
            InetSocketAddress addr = conn.getAddress();
            socket.send(new DatagramPacket(wireBytes, wireBytes.length, addr));

        } catch (Exception e) {
            log.fine("Failed to send audio to " + conn.getPlayerUuid() + ": " + e.getMessage());
        }
    }
}
