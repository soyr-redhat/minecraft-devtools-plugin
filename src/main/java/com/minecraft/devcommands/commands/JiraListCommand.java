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

import java.util.List;

public class JiraListCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;

    public JiraListCommand(DevCommandsPlugin plugin) {
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

        // Default to showing open issues in the project
        String projectKey = plugin.getConfig().getString("jira.project-key", "");
        String jql;
        String listName;

        if (args.length > 0) {
            String filter = args[0].toLowerCase();
            switch (filter) {
                case "mine":
                    jql = "assignee = currentUser() AND resolution = Unresolved ORDER BY updated DESC";
                    listName = "My Issues";
                    break;
                case "bugs":
                    jql = String.format("project = %s AND type = Bug AND resolution = Unresolved ORDER BY priority DESC", projectKey);
                    listName = "Open Bugs";
                    break;
                case "all":
                    jql = String.format("project = %s ORDER BY updated DESC", projectKey);
                    listName = "All Issues";
                    break;
                default:
                    jql = String.format("project = %s AND resolution = Unresolved ORDER BY updated DESC", projectKey);
                    listName = "Open Issues";
                    break;
            }
        } else {
            jql = String.format("project = %s AND resolution = Unresolved ORDER BY updated DESC", projectKey);
            listName = "Open Issues";
        }

        player.sendMessage(Component.text("Searching Jira...", NamedTextColor.YELLOW));

        // Run async to avoid blocking server
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JiraClient jira = plugin.getJiraClient();
                List<JiraClient.Issue> issues = jira.searchIssues(jql, 20);

                if (issues.isEmpty()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Component.text("No issues found!", NamedTextColor.YELLOW));
                    });
                    return;
                }

                // Format issues into book
                StringBuilder content = new StringBuilder();
                content.append(listName).append("\n");
                content.append("Found ").append(issues.size()).append(" issue(s)\n");
                content.append("---\n\n");

                for (JiraClient.Issue issue : issues) {
                    content.append(String.format(
                        "[%s] %s\n" +
                        "Status: %s | Type: %s\n" +
                        "Assignee: %s\n\n",
                        issue.key, issue.summary,
                        issue.status, issue.type,
                        issue.assignee
                    ));
                }

                int maxPages = plugin.getConfig().getInt("settings.max-book-pages", 50);
                ItemStack book = BookGenerator.createBook(
                    listName,
                    "Jira",
                    content.toString(),
                    maxPages
                );

                // Give book to player (must run on main thread)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(book);
                    player.sendMessage(Component.text("✓ Found " + issues.size() + " issue(s)!", NamedTextColor.GREEN));
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error listing Jira issues: " + e.getMessage());
                e.printStackTrace();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                });
            }
        });

        return true;
    }
}
