package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import com.minecraft.devcommands.api.GitHubProjectsClient;
import com.minecraft.devcommands.utils.BookGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KanbanViewCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;

    public KanbanViewCommand(DevCommandsPlugin plugin) {
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

        int projectNumber = plugin.getConfig().getInt("github.project-number", 1);

        if (args.length > 0) {
            try {
                projectNumber = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid project number: " + args[0], NamedTextColor.RED));
                return true;
            }
        }

        player.sendMessage(Component.text("Fetching project board...", NamedTextColor.YELLOW));

        final int finalProjectNumber = projectNumber;

        // Run async to avoid blocking server
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                GitHubProjectsClient projects = plugin.getGitHubProjectsClient();
                GitHubProjectsClient.ProjectBoard board = projects.getProjectBoard(finalProjectNumber);

                // Group items by status
                Map<String, Integer> statusCounts = new HashMap<>();
                for (GitHubProjectsClient.ProjectItem item : board.items) {
                    statusCounts.put(item.status, statusCounts.getOrDefault(item.status, 0) + 1);
                }

                // Format board into book
                StringBuilder content = new StringBuilder();
                content.append(board.title).append("\n");
                content.append("---\n\n");

                // Show status summary
                content.append("Summary:\n");
                for (Map.Entry<String, Integer> entry : statusCounts.entrySet()) {
                    content.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue()));
                }
                content.append("\n---\n\n");

                // Group and display by status
                Map<String, StringBuilder> statusGroups = new HashMap<>();
                for (GitHubProjectsClient.ProjectItem item : board.items) {
                    statusGroups.putIfAbsent(item.status, new StringBuilder());
                    statusGroups.get(item.status).append(String.format(
                        "#%d: %s\n  State: %s\n\n",
                        item.issueNumber, item.title, item.state
                    ));
                }

                for (Map.Entry<String, StringBuilder> entry : statusGroups.entrySet()) {
                    content.append("[").append(entry.getKey()).append("]\n");
                    content.append(entry.getValue());
                    content.append("\n");
                }

                int maxPages = plugin.getConfig().getInt("settings.max-book-pages", 50);
                String repoName = plugin.getConfig().getString("github.repository", "Unknown");
                String[] repoParts = repoName.split("/");
                String shortRepo = repoParts.length > 1 ? repoParts[1] : repoName;
                ItemStack book = BookGenerator.createBook(
                    shortRepo + " Project #" + board.number,
                    "GitHub",
                    content.toString(),
                    maxPages
                );

                // Give book to player (must run on main thread)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(book);
                    player.sendMessage(Component.text("✓ Project board with " + board.items.size() + " items!", NamedTextColor.GREEN));
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error fetching project board: " + e.getMessage());
                e.printStackTrace();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                });
            }
        });

        return true;
    }
}
