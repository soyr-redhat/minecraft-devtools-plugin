package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import com.minecraft.devcommands.api.JiraClient;
import com.minecraft.devcommands.utils.BookGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class JiraViewCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;

    public JiraViewCommand(DevCommandsPlugin plugin) {
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

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /jira-view <issue-key>", NamedTextColor.RED));
            player.sendMessage(Component.text("Example: /jira-view PROJ-123", NamedTextColor.GRAY));
            return true;
        }

        String issueKey = args[0].toUpperCase();
        player.sendMessage(Component.text("Fetching " + issueKey + "...", NamedTextColor.YELLOW));

        // Run async to avoid blocking server
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JiraClient jira = plugin.getJiraClient();
                JiraClient.Issue issue = jira.getIssue(issueKey);

                // Format issue into book
                String bookContent = String.format(
                    "%s\n" +
                    "---\n\n" +
                    "Type: %s\n" +
                    "Status: %s\n" +
                    "Assignee: %s\n\n" +
                    "Description:\n%s\n\n" +
                    "---\n" +
                    "URL: %s",
                    issue.summary, issue.type, issue.status, issue.assignee,
                    issue.description.isEmpty() ? "(no description)" : issue.description,
                    issue.url
                );

                int maxPages = plugin.getConfig().getInt("settings.max-book-pages", 50);
                ItemStack book = BookGenerator.createBook(
                    issue.key,
                    "Jira",
                    bookContent,
                    maxPages
                );

                // Give book to player (must run on main thread)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(book);
                    player.sendMessage(Component.text("✓ " + issue.key + " details in book!", NamedTextColor.GREEN));
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error fetching Jira issue: " + e.getMessage());
                e.printStackTrace();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                });
            }
        });

        return true;
    }
}
