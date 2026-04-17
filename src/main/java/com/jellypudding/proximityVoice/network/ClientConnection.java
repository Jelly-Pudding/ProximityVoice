package com.jellypudding.proximityVoice.network;

import com.jellypudding.proximityVoice.crypto.VoiceSecret;

import java.net.InetSocketAddress;
import java.util.UUID;

public class ClientConnection {

    private final UUID playerUuid;
    private final InetSocketAddress address;
    private final VoiceSecret secret;
    private volatile long lastKeepAlive;

    public ClientConnection(UUID playerUuid, InetSocketAddress address, VoiceSecret secret) {
        this.playerUuid = playerUuid;
        this.address = address;
        this.secret = secret;
        this.lastKeepAlive = System.currentTimeMillis();
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public InetSocketAddress getAddress() { return address; }
    public VoiceSecret getSecret() { return secret; }

    public void refreshKeepAlive() { this.lastKeepAlive = System.currentTimeMillis(); }

    public boolean isTimedOut(long keepAliveMs) {
        return System.currentTimeMillis() - lastKeepAlive > keepAliveMs * 10L;
    }
}
