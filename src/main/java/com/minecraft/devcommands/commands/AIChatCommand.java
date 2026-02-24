package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import com.minecraft.devcommands.api.VLLMClient;
import com.minecraft.devcommands.utils.BookGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AIChatCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;

    public AIChatCommand(DevCommandsPlugin plugin) {
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
            player.sendMessage(Component.text("Usage: /ai-chat <message>", NamedTextColor.RED));
            return true;
        }

        // Join args into message
        String message = String.join(" ", args);

        player.sendMessage(Component.text("Asking AI...", NamedTextColor.YELLOW));

        // Run async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                VLLMClient vllm = plugin.getVLLMClient();
                String response = vllm.chat(message);

                // If response is short, send as chat message
                if (response.length() < 200 && !response.contains("\n")) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Component.text("AI: ", NamedTextColor.AQUA)
                            .append(Component.text(response, NamedTextColor.WHITE)));
                    });
                } else {
                    // Otherwise, give as book
                    String bookContent = String.format(
                        "Your question:\n%s\n\n---\n\nAI Response:\n%s",
                        message, response
                    );

                    int maxPages = plugin.getConfig().getInt("settings.max-book-pages", 50);
                    ItemStack book = BookGenerator.createBook(
                        "AI Chat",
                        "AI Assistant",
                        bookContent,
                        maxPages
                    );

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.getInventory().addItem(book);
                        player.sendMessage(Component.text("AI response received! Check your inventory.", NamedTextColor.GREEN));
                    });
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Error with AI chat: " + e.getMessage());
                e.printStackTrace();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                });
            }
        });

        return true;
    }
}
