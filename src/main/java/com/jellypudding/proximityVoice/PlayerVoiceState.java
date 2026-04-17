package com.jellypudding.proximityVoice;

import java.util.UUID;

public class PlayerVoiceState {

    private final UUID uuid;
    private final String name;
    private volatile boolean disabled;
    private volatile boolean connected;

    public PlayerVoiceState(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }

    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    public boolean isSilent() { return disabled; }
}
