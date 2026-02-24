package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import com.minecraft.devcommands.api.GitHubClient;
import com.minecraft.devcommands.api.VLLMClient;
import com.minecraft.devcommands.utils.BookGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ReviewPRCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;

    public ReviewPRCommand(DevCommandsPlugin plugin) {
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
            player.sendMessage(Component.text("Usage: /git pr-review <latest|PR number>", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("Fetching PR data...", NamedTextColor.YELLOW));

        // Run async to avoid blocking server
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                GitHubClient github = plugin.getGitHubClient();
                VLLMClient vllm = plugin.getVLLMClient();

                // Fetch PR
                GitHubClient.PullRequest pr;
                if (args[0].equalsIgnoreCase("latest")) {
                    pr = github.getLatestPullRequest();
                } else {
                    try {
                        int prNumber = Integer.parseInt(args[0]);
                        pr = github.getPullRequest(prNumber);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("Invalid PR number: " + args[0], NamedTextColor.RED));
                        return;
                    }
                }

                player.sendMessage(Component.text("Analyzing PR #" + pr.number + " with AI...", NamedTextColor.YELLOW));

                // Fetch diff
                String diff = github.getPullRequestDiff(pr.number);

                // Prepare prompt
                String promptTemplate = plugin.getConfig().getString("prompts.pr-review", "Review this PR:\n{pr_data}");
                String prData = String.format(
                    "Title: %s\nAuthor: %s\nDescription: %s\n\nDiff:\n%s",
                    pr.title, pr.author, pr.body, diff
                );

                // Truncate if too long (keep last 3000 chars for context)
                if (prData.length() > 4000) {
                    prData = "... (truncated) ...\n" + prData.substring(prData.length() - 3000);
                }

                String prompt = promptTemplate.replace("{pr_data}", prData);

                // Get AI review
                String review = vllm.complete(prompt);

                // Format review into book
                String bookContent = String.format(
                    "PR #%d: %s\n" +
                    "Author: %s\n" +
                    "Branch: %s\n" +
                    "---\n\n" +
                    "AI REVIEW:\n\n%s\n\n" +
                    "---\n" +
                    "URL: %s",
                    pr.number, pr.title, pr.author, pr.branch, review, pr.url
                );

                int maxPages = plugin.getConfig().getInt("settings.max-book-pages", 50);
                String repoName = plugin.getConfig().getString("github.repository", "Unknown");
                String[] repoParts = repoName.split("/");
                String shortRepo = repoParts.length > 1 ? repoParts[1] : repoName;
                ItemStack book = BookGenerator.createBook(
                    shortRepo + " PR #" + pr.number,
                    "AI Assistant",
                    bookContent,
                    maxPages
                );

                // Give book to player (must run on main thread)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(book);
                    player.sendMessage(Component.text("PR review complete! Check your inventory.", NamedTextColor.GREEN));
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error reviewing PR: " + e.getMessage());
                e.printStackTrace();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                });
            }
        });

        return true;
    }
}
