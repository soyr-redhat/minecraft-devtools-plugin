package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GitShowRepoCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;

    public GitShowRepoCommand(DevCommandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        String currentRepo = plugin.getConfig().getString("github.repository", "Not set");
        String githubUrl = "https://github.com/" + currentRepo;

        player.sendMessage(Component.text("Current GitHub Repository:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  " + currentRepo, NamedTextColor.AQUA));
        player.sendMessage(Component.text("  " + githubUrl, NamedTextColor.GRAY));
        player.sendMessage(Component.text("Use /git <owner/repo> to change it", NamedTextColor.GRAY));

        return true;
    }
}
