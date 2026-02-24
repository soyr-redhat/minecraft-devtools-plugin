package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import com.minecraft.devcommands.api.GitHubProjectsClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class KanbanListCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;

    public KanbanListCommand(DevCommandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        // Check cooldown
        if (!plugin.checkCooldown(player.getUniqueId())) {
            int cooldown = plugin.getConfig().getInt("settings.command-cooldown", 5);
            player.sendMessage(Component.text("Please wait " + cooldown + " seconds between commands!", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("Fetching projects...", NamedTextColor.YELLOW));

        // Run async to avoid blocking server
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                GitHubProjectsClient projects = plugin.getGitHubProjectsClient();
                List<GitHubProjectsClient.Project> projectList = projects.listProjects();

                if (projectList.isEmpty()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Component.text("No projects found!", NamedTextColor.YELLOW));
                    });
                    return;
                }

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Projects:", NamedTextColor.GREEN));
                    for (GitHubProjectsClient.Project proj : projectList) {
                        player.sendMessage(Component.text(
                            String.format("  #%d: %s", proj.number, proj.title),
                            NamedTextColor.AQUA
                        ));
                    }
                    player.sendMessage(Component.text(
                        "Use /git kanban-view <number> to view a board",
                        NamedTextColor.GRAY
                    ));
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error listing projects: " + e.getMessage());
                e.printStackTrace();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                });
            }
        });

        return true;
    }
}
