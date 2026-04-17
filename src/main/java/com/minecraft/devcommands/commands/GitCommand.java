package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main Git command that routes to subcommands
 */
public class GitCommand implements CommandExecutor, TabCompleter {
    private final DevCommandsPlugin plugin;
    private final GitSetRepoCommand setRepoCommand;
    private final GitShowRepoCommand showRepoCommand;
    private final PRListCommand prListCommand;
    private final ReviewPRCommand reviewPRCommand;
    private final KanbanListCommand kanbanListCommand;
    private final KanbanViewCommand kanbanViewCommand;

    public GitCommand(DevCommandsPlugin plugin) {
        this.plugin = plugin;
        this.setRepoCommand = new GitSetRepoCommand(plugin);
        this.showRepoCommand = new GitShowRepoCommand(plugin);
        this.prListCommand = new PRListCommand(plugin);
        this.reviewPRCommand = new ReviewPRCommand(plugin);
        this.kanbanListCommand = new KanbanListCommand(plugin);
        this.kanbanViewCommand = new KanbanViewCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subcommand) {
            case "repo":
                return showRepoCommand.onCommand(sender, command, label, subArgs);

            case "pr-list":
            case "prs":
                return prListCommand.onCommand(sender, command, label, subArgs);

            case "pr-review":
            case "review":
            case "review-pr":
                return reviewPRCommand.onCommand(sender, command, label, subArgs);

            case "kanban-list":
            case "projects":
                return kanbanListCommand.onCommand(sender, command, label, subArgs);

            case "kanban-view":
            case "kanban":
            case "project":
                return kanbanViewCommand.onCommand(sender, command, label, subArgs);

            case "help":
                showHelp(player);
                return true;

            default:
                // If it doesn't match a subcommand, treat it as a repo to set
                // e.g., "/git vllm-project/vllm"
                return setRepoCommand.onCommand(sender, command, label, args);
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text("Git Commands:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  /git <owner/repo> - Set repository", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  /git repo - Show current repository", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  /git pr-list - List pull requests", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  /git pr-review <latest|#> - Review a PR", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  /git kanban-list - List project boards", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  /git kanban-view <#> - View project board", NamedTextColor.AQUA));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList(
                "repo", "pr-list", "pr-review", "kanban-list", "kanban-view", "help"
            );
            String partial = args[0].toLowerCase();
            for (String sub : subcommands) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        }

        return completions;
    }
}
