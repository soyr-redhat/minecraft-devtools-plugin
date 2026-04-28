package com.minecraft.devcommands.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecraft.devcommands.DevCommandsPlugin;
import com.minecraft.devcommands.api.ToolDefinitions;
import com.minecraft.devcommands.api.VLLMClient;
import com.minecraft.devcommands.utils.BookGenerator;
import com.minecraft.devcommands.utils.ChatHistory;
import com.minecraft.devcommands.utils.ToolExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AIChatCommand implements CommandExecutor, TabCompleter {
    private final DevCommandsPlugin plugin;

    public AIChatCommand(DevCommandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Suggest subcommands
            List<String> subcommands = Arrays.asList("help", "models", "tools", "history");
            String input = args[0].toLowerCase();
            for (String sub : subcommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        }

        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /ai-chat <message|help|models|tools|history>", NamedTextColor.RED));
            return true;
        }

        // Handle special subcommands
        String firstArg = args[0].toLowerCase();

        if (firstArg.equals("help")) {
            showHelp(player);
            return true;
        }

        if (firstArg.equals("models")) {
            showModels(player);
            return true;
        }

        if (firstArg.equals("tools")) {
            showTools(player);
            return true;
        }

        if (firstArg.equals("history")) {
            showHistory(player);
            return true;
        }

        // Regular chat functionality
        // Check cooldown
        if (!plugin.checkCooldown(player.getUniqueId())) {
            int cooldown = plugin.getConfig().getInt("settings.command-cooldown", 5);
            player.sendMessage(Component.text("Please wait " + cooldown + " seconds between commands!", NamedTextColor.RED));
            return true;
        }

        // Join args into message
        String message = String.join(" ", args);

        player.sendMessage(Component.text("Asking AI...", NamedTextColor.YELLOW));

        // Run async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                VLLMClient vllm = plugin.getVLLMClient();
                ToolExecutor toolExecutor = new ToolExecutor(plugin);

                // Get available tools
                JsonArray tools = ToolDefinitions.getMinecraftTools();

                // Call AI with tools
                VLLMClient.ChatResponse response = vllm.chatWithTools(message, tools);

                String finalResponse;

                // Check if AI wants to use tools
                if (response.hasToolCalls()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Component.text("🔧 AI is using tools...", NamedTextColor.GOLD));
                    });

                    // Execute each tool call
                    JsonArray toolCalls = response.toolCalls;
                    StringBuilder toolResults = new StringBuilder();

                    for (int i = 0; i < toolCalls.size(); i++) {
                        JsonObject toolCall = toolCalls.get(i).getAsJsonObject();
                        JsonObject function = toolCall.getAsJsonObject("function");
                        String toolName = function.get("name").getAsString();
                        JsonObject arguments = plugin.getConfig().getBoolean("settings.debug", false)
                            ? new com.google.gson.Gson().fromJson(function.get("arguments").getAsString(), JsonObject.class)
                            : new com.google.gson.Gson().fromJson(function.get("arguments"), JsonObject.class);

                        // Show which tool is being used
                        final String displayName = toolName.replace("_", " ");
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.sendMessage(Component.text("  → ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(displayName, NamedTextColor.YELLOW)));
                        });

                        try {
                            String result = toolExecutor.executeTool(toolName, arguments, player);
                            toolResults.append(String.format("Tool '%s' result:\n%s\n\n", toolName, result));
                        } catch (Exception e) {
                            toolResults.append(String.format("Tool '%s' error: %s\n\n", toolName, e.getMessage()));
                            plugin.getLogger().warning("Tool execution error: " + e.getMessage());
                        }
                    }

                    // Send tool results back to AI for final response
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Component.text("✨ Generating final response...", NamedTextColor.AQUA));
                    });

                    String followUpPrompt = String.format(
                        "Based on the tool results below, please provide a helpful response to the user.\n\n" +
                        "Original question: %s\n\n" +
                        "Tool results:\n%s",
                        message, toolResults.toString()
                    );

                    finalResponse = vllm.chat(followUpPrompt);
                } else {
                    // No tools needed, use direct response
                    finalResponse = response.content;
                }

                // Add to history
                plugin.getChatHistory().addEntry(player.getName(), message, finalResponse);

                // Send final response
                String finalResponseCopy = finalResponse;
                if (finalResponse.length() < 200 && !finalResponse.contains("\n")) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Component.text("AI: ", NamedTextColor.AQUA)
                            .append(Component.text(finalResponseCopy, NamedTextColor.WHITE)));
                    });
                } else {
                    // Give as book
                    String bookContent = String.format(
                        "Your question:\n%s\n\n---\n\nAI Response:\n%s",
                        message, finalResponseCopy
                    );

                    // Create book title: ai:chat <first 50 chars of prompt>
                    String promptPreview = message.length() > 50 ? message.substring(0, 50) + "..." : message;
                    String bookTitle = "ai:chat " + promptPreview;

                    int maxPages = plugin.getConfig().getInt("settings.max-book-pages", 50);
                    ItemStack book = BookGenerator.createBook(
                        bookTitle,
                        "Kimi k2.6",
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

    private void showModels(Player player) {
        player.sendMessage(Component.text("════════════════════════", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("Available AI Models", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("════════════════════════", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("• ", NamedTextColor.YELLOW)
            .append(Component.text("Kimi k2.6", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text(" (Current)", NamedTextColor.GREEN)));

        player.sendMessage(Component.text("  └ ", NamedTextColor.DARK_GRAY)
            .append(Component.text("256K context window", NamedTextColor.GRAY)));

        player.sendMessage(Component.text("  └ ", NamedTextColor.DARK_GRAY)
            .append(Component.text("Great for code, reasoning, long docs", NamedTextColor.GRAY)));

        player.sendMessage(Component.text("  └ ", NamedTextColor.DARK_GRAY)
            .append(Component.text("Model: ", NamedTextColor.GRAY))
            .append(Component.text("kimi-k2-6", NamedTextColor.AQUA)));
    }

    private void showTools(Player player) {
        player.sendMessage(Component.text("════════════════════════", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("AI Tools - Auto-Detected", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("════════════════════════", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("The AI automatically uses these tools when needed:", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("GitHub Tools:", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
            .append(Component.text("git_list_files", NamedTextColor.WHITE))
            .append(Component.text(" - Explore repo structure", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
            .append(Component.text("git_search_code", NamedTextColor.WHITE))
            .append(Component.text(" - Search for code/functions", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
            .append(Component.text("git_pr_list", NamedTextColor.WHITE))
            .append(Component.text(" - List pull requests", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
            .append(Component.text("git_pr_review", NamedTextColor.WHITE))
            .append(Component.text(" - Review PRs", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
            .append(Component.text("git_set_repo", NamedTextColor.WHITE))
            .append(Component.text(" - Change repository", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("Jira Tools:", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
            .append(Component.text("jira_list", NamedTextColor.WHITE))
            .append(Component.text(" - List issues (mine/bugs/all)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
            .append(Component.text("jira_view", NamedTextColor.WHITE))
            .append(Component.text(" - View issue details", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
            .append(Component.text("jira_create", NamedTextColor.WHITE))
            .append(Component.text(" - Create issues", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("Code Tools:", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
            .append(Component.text("code_explain", NamedTextColor.WHITE))
            .append(Component.text(" - Fetch & explain code files", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("💡 Tip: ", NamedTextColor.GOLD)
            .append(Component.text("Just ask naturally! AI picks the right tools.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("Examples:", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  \"What's in this repo?\"", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  \"Find all login functions\"", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  \"Show me open PRs and bugs\"", NamedTextColor.DARK_GRAY));
    }

    private void showHistory(Player player) {
        ChatHistory history = plugin.getChatHistory();
        int historySize = history.size();

        if (historySize == 0) {
            player.sendMessage(Component.text("No chat history yet!", NamedTextColor.YELLOW));
            return;
        }

        List<ChatHistory.ChatEntry> recent = history.getRecentHistory(10);

        StringBuilder bookContent = new StringBuilder();
        bookContent.append("AI Chat History\n");
        bookContent.append("═══════════════\n\n");
        bookContent.append(String.format("Total entries: %d\n", historySize));
        bookContent.append(String.format("Showing: %d most recent\n\n", recent.size()));

        for (int i = 0; i < recent.size(); i++) {
            ChatHistory.ChatEntry entry = recent.get(i);
            bookContent.append(String.format("─── Entry #%d ───\n", i + 1));
            bookContent.append(String.format("Player: %s\n", entry.playerName));
            bookContent.append(String.format("Time: %s\n\n", entry.getFormattedTime()));
            bookContent.append(String.format("Prompt:\n%s\n\n", entry.getShortPrompt(150)));
            bookContent.append(String.format("Response:\n%s\n\n",
                entry.response.length() > 200
                    ? entry.response.substring(0, 197) + "..."
                    : entry.response));
            bookContent.append("\n");
        }

        int maxPages = plugin.getConfig().getInt("settings.max-book-pages", 50);
        ItemStack book = BookGenerator.createBook(
            "AI Chat History",
            "System",
            bookContent.toString(),
            maxPages
        );

        player.getInventory().addItem(book);
        player.sendMessage(Component.text("Chat history book added to inventory!", NamedTextColor.GREEN)
            .append(Component.text(" (" + historySize + " total entries)", NamedTextColor.GRAY)));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text("════════════════════════", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("AI Chat Help", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("════════════════════════", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("Chat with AI:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("  /ai-chat <message>", NamedTextColor.WHITE)
            .append(Component.text(" - Ask the AI anything", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("Examples:", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text("  /ai-chat What's in this repo?", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  /ai-chat Find all login functions", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  /ai-chat List PRs and critical bugs", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  /ai-chat Explain the main class", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("Subcommands:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("  /ai-chat help", NamedTextColor.WHITE)
            .append(Component.text(" - Show this help message", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /ai-chat models", NamedTextColor.WHITE)
            .append(Component.text(" - Show available AI models", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /ai-chat tools", NamedTextColor.WHITE)
            .append(Component.text(" - List 9 auto-detected tools", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /ai-chat history", NamedTextColor.WHITE)
            .append(Component.text(" - View recent chat history", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("How it works:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
            .append(Component.text("AI automatically uses tools (GitHub, Jira, etc.)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
            .append(Component.text("You'll see: 🔧 AI is using tools...", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
            .append(Component.text("Short replies in chat, long ones as books", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
            .append(Component.text("Powered by Kimi k2.6 (256K context)", NamedTextColor.GRAY)));
    }
}
