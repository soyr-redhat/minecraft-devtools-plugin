package com.minecraft.devcommands.commands;

import com.minecraft.devcommands.DevCommandsPlugin;
import com.minecraft.devcommands.api.GitHubClient;
import com.minecraft.devcommands.api.VLLMClient;
import com.minecraft.devcommands.utils.BookGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CodeExplainCommand implements CommandExecutor {
    private final DevCommandsPlugin plugin;
    private final OkHttpClient httpClient;

    public CodeExplainCommand(DevCommandsPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient();
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
            player.sendMessage(Component.text("Usage: /code-explain <file path in repo>", NamedTextColor.RED));
            return true;
        }

        String filePath = args[0];
        player.sendMessage(Component.text("Fetching code from repository...", NamedTextColor.YELLOW));

        // Run async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Fetch file content from GitHub
                String githubToken = resolveConfigValue(plugin.getConfig().getString("github.token", ""));
                String repository = plugin.getConfig().getString("github.repository", "");
                String apiUrl = plugin.getConfig().getString("github.api-url", "https://api.github.com");

                String url = String.format("%s/repos/%s/contents/%s", apiUrl, repository, filePath);

                Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + githubToken)
                    .header("Accept", "application/vnd.github.v3.raw")
                    .build();

                String code;
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new Exception("Failed to fetch file: " + response.code() + " " + response.message());
                    }
                    code = response.body().string();
                }

                player.sendMessage(Component.text("Analyzing code with AI...", NamedTextColor.YELLOW));

                // Get AI explanation
                VLLMClient vllm = plugin.getVLLMClient();
                String promptTemplate = plugin.getConfig().getString("prompts.code-explain",
                    "Explain this code:\n{code}");

                // Truncate if too long
                String codeSnippet = code;
                if (code.length() > 3000) {
                    codeSnippet = code.substring(0, 3000) + "\n... (truncated) ...";
                }

                String prompt = promptTemplate.replace("{code}", codeSnippet);
                String explanation = vllm.complete(prompt);

                // Create book
                String bookContent = String.format(
                    "File: %s\n" +
                    "---\n\n" +
                    "EXPLANATION:\n\n%s\n\n" +
                    "---\n\n" +
                    "CODE:\n%s",
                    filePath, explanation, codeSnippet
                );

                int maxPages = plugin.getConfig().getInt("settings.max-book-pages", 50);
                ItemStack book = BookGenerator.createBook(
                    "Code: " + filePath,
                    "AI Assistant",
                    bookContent,
                    maxPages
                );

                // Give book to player
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(book);
                    player.sendMessage(Component.text("Code explanation complete! Check your inventory.", NamedTextColor.GREEN));
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error explaining code: " + e.getMessage());
                e.printStackTrace();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                });
            }
        });

        return true;
    }

    private String resolveConfigValue(String value) {
        if (value.startsWith("${") && value.endsWith("}")) {
            String envVar = value.substring(2, value.length() - 1);
            String envValue = System.getenv(envVar);
            return envValue != null ? envValue : "";
        }
        return value;
    }
}
