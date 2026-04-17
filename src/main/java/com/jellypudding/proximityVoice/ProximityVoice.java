package com.jellypudding.proximityVoice;

import com.jellypudding.proximityVoice.commands.ReloadCommand;
import com.jellypudding.proximityVoice.network.VoiceServer;
import org.bukkit.command.PluginCommand;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

import java.util.Set;
import java.util.logging.Level;

public final class ProximityVoice extends JavaPlugin implements Listener {

    private VoiceServer voiceServer;
    private ChannelHandler channelHandler;

    private void applyLogLevel() {
        getLogger().setLevel(getConfig().getBoolean("debug", false) ? Level.FINE : Level.INFO);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        applyLogLevel();

        voiceServer = new VoiceServer(this);
        try {
            voiceServer.startServer();
        } catch (Exception e) {
            getLogger().severe("Failed to start UDP server: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        channelHandler = new ChannelHandler(this, voiceServer);

        Messenger messenger = getServer().getMessenger();
        messenger.registerIncomingPluginChannel(this, ChannelHandler.CH_REQUEST_SECRET, channelHandler);
        messenger.registerIncomingPluginChannel(this, ChannelHandler.CH_UPDATE_STATE, channelHandler);
        messenger.registerOutgoingPluginChannel(this, ChannelHandler.CH_SECRET);
        messenger.registerOutgoingPluginChannel(this, ChannelHandler.CH_PLAYER_STATE);

        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand cmd = getCommand("proximityvoice");
        if (cmd != null) cmd.setExecutor(new ReloadCommand(this));

        for (Player p : getServer().getOnlinePlayers()) {
            voiceServer.addPlayerState(p.getUniqueId(), p.getName());
            addVoiceChannels(p);
        }

        // Initialise bStats
        new Metrics(this, 30811);

        getLogger().info("ProximityVoice enabled on UDP port " + getConfig().getInt("voice-port", 24454));
    }

    @Override
    public void onDisable() {
        if (voiceServer != null) voiceServer.stopServer();
        Messenger messenger = getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(this);
        messenger.unregisterOutgoingPluginChannel(this);
        getLogger().info("ProximityVoice disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        voiceServer.addPlayerState(player.getUniqueId(), player.getName());

        addVoiceChannels(player);

        getServer().getScheduler().runTaskLater(this,
                () -> channelHandler.sendAllStatesTo(player), 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        channelHandler.broadcastPlayerState(player, false, true);
        voiceServer.removePlayer(player.getUniqueId());
        removeVoiceChannels(player);
    }

    private void addVoiceChannels(Player player) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        for (String channel : getServer().getMessenger().getOutgoingChannels(this)) {
            craftPlayer.addChannel(channel);
        }
    }

    private void removeVoiceChannels(Player player) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        for (String channel : getServer().getMessenger().getOutgoingChannels(this)) {
            craftPlayer.removeChannel(channel);
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        applyLogLevel();
        double newDistance = getConfig().getDouble("voice-distance", 48.0);
        if (voiceServer.getRouter() != null) {
            voiceServer.getRouter().setVoiceDistance(newDistance);
        }
        getLogger().info("Config reloaded. If you changed voice-port or keep-alive restart the server.");
    }
}
