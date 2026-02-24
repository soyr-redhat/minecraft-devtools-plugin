package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import com.minecraft.devcommands.api.GitHubClient;
import com.minecraft.devcommands.utils.BookGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class PRListCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;

    public PRListCommand(DevCommandsPlugin plugin) {
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

        player.sendMessage(Component.text("Fetching pull requests...", NamedTextColor.YELLOW));

        // Run async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                GitHubClient github = plugin.getGitHubClient();
                List<GitHubClient.PullRequest> prs = github.listPullRequests();

                if (prs.isEmpty()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Component.text("No open pull requests found!", NamedTextColor.YELLOW));
                    });
                    return;
                }

                // Format PR list
                StringBuilder content = new StringBuilder();
                content.append("OPEN PULL REQUESTS\n");
                content.append("==================\n\n");

                for (GitHubClient.PullRequest pr : prs) {
                    content.append(String.format(
                        "#%d: %s\n" +
                        "Author: %s\n" +
                        "Branch: %s\n" +
                        "Created: %s\n\n",
                        pr.number, pr.title, pr.author, pr.branch, pr.createdAt
                    ));
                }

                content.append("\nUse /git pr-review <number> to review a specific PR");

                int maxPages = plugin.getConfig().getInt("settings.max-book-pages", 50);
                String repoName = plugin.getConfig().getString("github.repository", "Unknown");
                ItemStack book = BookGenerator.createBook(
                    "PRs: " + repoName,
                    "GitHub Bot",
                    content.toString(),
                    maxPages
                );

                // Give book to player
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(book);
                    player.sendMessage(Component.text("Found " + prs.size() + " open PRs! Check your inventory.", NamedTextColor.GREEN));
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error listing PRs: " + e.getMessage());
                e.printStackTrace();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                });
            }
        });

        return true;
    }
}
