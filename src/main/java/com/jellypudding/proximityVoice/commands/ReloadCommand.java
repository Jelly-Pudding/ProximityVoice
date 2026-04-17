package com.jellypudding.proximityVoice.commands;

import com.jellypudding.proximityVoice.ProximityVoice;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final ProximityVoice plugin;

    public ReloadCommand(ProximityVoice plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPluginConfig();
            sender.sendMessage("ProximityVoice config reloaded.");
            return true;
        }
        sender.sendMessage("Usage: /proximityvoice reload");
        return true;
    }
}
