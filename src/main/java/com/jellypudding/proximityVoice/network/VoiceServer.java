package com.jellypudding.proximityVoice.network;

import com.jellypudding.proximityVoice.PlayerVoiceState;
import com.jellypudding.proximityVoice.ProximityVoice;
import com.jellypudding.proximityVoice.crypto.VoiceSecret;
import com.jellypudding.proximityVoice.protocol.PacketBuffer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class VoiceServer extends Thread {

    private static final byte TYPE_MIC            = 0x01;
    private static final byte TYPE_AUTH           = 0x05;
    private static final byte TYPE_AUTH_ACK       = 0x06;
    private static final byte TYPE_KEEPALIVE      = 0x08;
    private static final byte TYPE_CONN_CHECK     = 0x09;
    private static final byte TYPE_CONN_CHECK_ACK = 0x0A;

    private static final int RECV_BUFFER = 4096;

    private final Map<UUID, VoiceSecret> pendingSecrets = new ConcurrentHashMap<>();
    private final Map<UUID, ClientConnection> pendingConnections = new ConcurrentHashMap<>();
    private final Map<UUID, ClientConnection> connections = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerVoiceState> states = new ConcurrentHashMap<>();

    private final ProximityVoice plugin;
    private final Logger log;
    private DatagramSocket socket;
    private ProximityRouter router;
    private ExecutorService packetSender;
    private volatile boolean running;
    private final int port;
    private final long keepAliveMs;

    public VoiceServer(ProximityVoice plugin) {
        super("ProximityVoice-UDP");
        setDaemon(true);
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.port = plugin.getConfig().getInt("voice-port", 24454);
        this.keepAliveMs = plugin.getConfig().getInt("keep-alive", 3000);
    }

    public void startServer() throws Exception {
        socket = new DatagramSocket(port);
        socket.setSoTimeout((int) keepAliveMs);

        packetSender = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ProximityVoice-Sender");
            t.setDaemon(true);
            return t;
        });

        double voiceDistance = plugin.getConfig().getDouble("voice-distance", 48.0);
        router = new ProximityRouter(this, socket, packetSender, voiceDistance, log);
        running = true;
        start();
        log.info("UDP server listening on port " + port);
    }

    public void stopServer() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        if (packetSender != null) {
            packetSender.shutdown();
            try {
                packetSender.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("UDP server stopped");
    }

    @Override
    public void run() {
        byte[] buf = new byte[RECV_BUFFER];
        long lastKeepAliveSend = 0;

        while (running) {
            long now = System.currentTimeMillis();
            if (now - lastKeepAliveSend >= keepAliveMs) {
                sendKeepAlives();
                checkTimeouts();
                lastKeepAliveSend = now;
            }

            try {
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                socket.receive(recv);
                processPacket(recv);
            } catch (java.net.SocketTimeoutException ignored) {
            } catch (Exception e) {
                if (running) log.fine("Receive error: " + e.getMessage());
            }
        }
    }

    private void processPacket(DatagramPacket recv) {
        byte[] data = Arrays.copyOf(recv.getData(), recv.getLength());
        InetSocketAddress from = (InetSocketAddress) recv.getSocketAddress();

        log.fine("UDP packet from " + from + ": " + data.length + " bytes, first=0x"
                + String.format("%02X", data.length > 0 ? data[0] & 0xFF : 0));

        try {
            PacketBuffer wire = new PacketBuffer(data);
            if (wire.readByte() != 0xFF) {
                log.fine("Dropped packet from " + from + ": missing 0xFF magic byte");
                return;
            }

            UUID playerId = wire.readUUID();
            VoiceSecret secret = pendingSecrets.get(playerId);
            if (secret == null) return;

            byte[] plain = secret.decrypt(wire.readByteArray());
            if (plain == null || plain.length == 0) return;

            PacketBuffer payload = new PacketBuffer(plain);
            byte type = (byte) payload.readByte();

            switch (type) {
                case TYPE_AUTH       -> handleAuthenticate(playerId, from, secret, payload);
                case TYPE_CONN_CHECK -> handleConnectionCheck(playerId, from, secret);
                case TYPE_MIC        -> handleMicPacket(playerId, payload);
                case TYPE_KEEPALIVE  -> refreshKeepAlive(playerId);
            }
        } catch (Exception e) {
            log.fine("Bad packet from " + from + ": " + e.getMessage());
        }
    }

    private void handleAuthenticate(UUID playerId, InetSocketAddress from, VoiceSecret secret,
                                    PacketBuffer payload) throws Exception {
        UUID claimedId = payload.readUUID();
        byte[] claimedSecret = payload.readBytes(VoiceSecret.SECRET_BYTES);

        if (!claimedId.equals(playerId)) {
            log.fine("Auth failed for " + playerId + ": UUID mismatch (claimed " + claimedId + ")");
            return;
        }
        if (!VoiceSecret.fromBytes(claimedSecret).equals(secret)) {
            log.fine("Auth failed for " + playerId + ": wrong secret");
            return;
        }

        pendingConnections.put(playerId, new ClientConnection(playerId, from, secret));
        log.info("Authenticated " + playerId);
        sendRaw(from, secret, new byte[]{TYPE_AUTH_ACK});
    }

    private void handleConnectionCheck(UUID playerId, InetSocketAddress from,
                                       VoiceSecret secret) throws Exception {
        ClientConnection conn = pendingConnections.remove(playerId);
        if (conn == null) {
            conn = connections.get(playerId);
            if (conn != null) sendRaw(from, secret, new byte[]{TYPE_CONN_CHECK_ACK});
            return;
        }

        connections.put(playerId, conn);
        conn.refreshKeepAlive();

        PlayerVoiceState state = states.get(playerId);
        if (state != null) state.setConnected(true);

        log.fine("Connection validated for " + playerId);
        sendRaw(from, secret, new byte[]{TYPE_CONN_CHECK_ACK});
    }

    private void handleMicPacket(UUID playerId, PacketBuffer payload) throws Exception {
        ClientConnection conn = connections.get(playerId);
        if (conn == null) return;
        conn.refreshKeepAlive();

        byte[] opusData = payload.readByteArray();
        long sequenceNumber = payload.readLong();
        boolean whispering = payload.readBoolean();

        plugin.getServer().getScheduler().runTask(plugin,
                () -> router.route(playerId, opusData, sequenceNumber, whispering));
    }

    private void refreshKeepAlive(UUID playerId) {
        ClientConnection conn = connections.get(playerId);
        if (conn != null) conn.refreshKeepAlive();
    }

    private void sendKeepAlives() {
        for (ClientConnection conn : connections.values()) {
            try {
                sendRaw(conn.getAddress(), conn.getSecret(), new byte[]{TYPE_KEEPALIVE});
            } catch (Exception e) {
                log.fine("Keepalive failed for " + conn.getPlayerUuid());
            }
        }
    }

    private void checkTimeouts() {
        connections.values().removeIf(conn -> {
            if (!conn.isTimedOut(keepAliveMs)) return false;
            log.fine("Timed out: " + conn.getPlayerUuid());
            pendingSecrets.remove(conn.getPlayerUuid());
            PlayerVoiceState state = states.get(conn.getPlayerUuid());
            if (state != null) state.setConnected(false);
            return true;
        });
    }

    // server ---> client: [0xFF][VarInt length][IV + AES-GCM ciphertext]
    private void sendRaw(InetSocketAddress to, VoiceSecret secret, byte[] plain) throws Exception {
        byte[] encrypted = secret.encrypt(plain);
        PacketBuffer wire = new PacketBuffer();
        wire.writeByte(0xFF);
        wire.writeByteArray(encrypted);
        byte[] bytes = wire.toBytes();
        socket.send(new DatagramPacket(bytes, bytes.length, to));
    }

    public VoiceSecret registerSecret(UUID playerUuid) {
        VoiceSecret secret = VoiceSecret.generate();
        pendingSecrets.put(playerUuid, secret);
        return secret;
    }

    public void removePlayer(UUID playerUuid) {
        pendingSecrets.remove(playerUuid);
        pendingConnections.remove(playerUuid);
        connections.remove(playerUuid);
        states.remove(playerUuid);
    }

    public void addPlayerState(UUID uuid, String name) {
        states.put(uuid, new PlayerVoiceState(uuid, name));
    }

    public void updatePlayerState(UUID uuid, boolean disabled) {
        PlayerVoiceState state = states.get(uuid);
        if (state != null) state.setDisabled(disabled);
    }

    public ClientConnection getConnection(UUID uuid) { return connections.get(uuid); }
    public PlayerVoiceState getState(UUID uuid) { return states.get(uuid); }
    public ProximityRouter getRouter() { return router; }
    public ProximityVoice getPlugin() { return plugin; }
}
