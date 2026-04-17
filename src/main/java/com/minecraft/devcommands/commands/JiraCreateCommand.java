package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import com.minecraft.devcommands.api.JiraClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JiraCreateCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;

    public JiraCreateCommand(DevCommandsPlugin plugin) {
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

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /jira-create <type> <summary> [description]", NamedTextColor.RED));
            player.sendMessage(Component.text("Types: Bug, Task, Story", NamedTextColor.GRAY));
            player.sendMessage(Component.text("Example: /jira-create Bug Player can't login", NamedTextColor.GRAY));
            return true;
        }

        String issueType = args[0];

        // Build summary from remaining args
        StringBuilder summaryBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) summaryBuilder.append(" ");
            summaryBuilder.append(args[i]);
        }
        String summary = summaryBuilder.toString();

        // Check if last part is a description (starts with | separator)
        String description = "";
        int separatorIndex = summary.indexOf(" | ");
        if (separatorIndex != -1) {
            description = summary.substring(separatorIndex + 3);
            summary = summary.substring(0, separatorIndex);
        }

        player.sendMessage(Component.text("Creating " + issueType + " in Jira...", NamedTextColor.YELLOW));

        final String finalSummary = summary;
        final String finalDescription = description;

        // Run async to avoid blocking server
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JiraClient jira = plugin.getJiraClient();
                JiraClient.Issue issue = jira.createIssue(finalSummary, finalDescription, issueType);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("✓ Created " + issue.key + ": " + issue.summary, NamedTextColor.GREEN));
                    player.sendMessage(Component.text("View at: " + issue.url, NamedTextColor.AQUA));
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error creating Jira issue: " + e.getMessage());
                e.printStackTrace();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                });
            }
        });

        return true;
    }
}
