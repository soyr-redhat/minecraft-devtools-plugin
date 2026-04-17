package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitSetRepoCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;
    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile("github\\.com[/:]([^/]+)/([^/\\.]+)");
    private static final Pattern OWNER_REPO_PATTERN = Pattern.compile("^([^/]+)/([^/]+)$");

    public GitSetRepoCommand(DevCommandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /git <owner/repo> or <github-url>", NamedTextColor.RED));
            player.sendMessage(Component.text("Examples:", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /git vllm-project/vllm", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /git https://github.com/vllm-project/vllm", NamedTextColor.GRAY));
            return true;
        }

        String input = String.join(" ", args);
        String owner = null;
        String repo = null;

        // Try to match GitHub URL
        Matcher urlMatcher = GITHUB_URL_PATTERN.matcher(input);
        if (urlMatcher.find()) {
            owner = urlMatcher.group(1);
            repo = urlMatcher.group(2);
        } else {
            // Try to match owner/repo format
            Matcher ownerRepoMatcher = OWNER_REPO_PATTERN.matcher(input);
            if (ownerRepoMatcher.find()) {
                owner = ownerRepoMatcher.group(1);
                repo = ownerRepoMatcher.group(2);
            }
        }

        if (owner == null || repo == null) {
            player.sendMessage(Component.text("Invalid format! Use 'owner/repo' or GitHub URL", NamedTextColor.RED));
            return true;
        }

        String repository = owner + "/" + repo;

        // Update the config
        plugin.getConfig().set("github.repository", repository);
        plugin.saveConfig();

        // Reinitialize the GitHub clients with the new repository
        plugin.reinitializeGitHubClients();

        player.sendMessage(Component.text("✓ Repository changed to: " + repository, NamedTextColor.GREEN));
        player.sendMessage(Component.text("You can now use /git pr-list and /git kanban-list with this repo!", NamedTextColor.AQUA));

        return true;
    }
}
