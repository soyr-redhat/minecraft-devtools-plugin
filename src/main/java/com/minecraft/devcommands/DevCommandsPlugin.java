package com.minecraft.devcommands;

import com.minecraft.devcommands.api.GitHubClient;
import com.minecraft.devcommands.api.GitHubProjectsClient;
import com.minecraft.devcommands.api.JiraClient;
import com.minecraft.devcommands.api.VLLMClient;
import com.minecraft.devcommands.commands.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DevCommandsPlugin extends JavaPlugin {
    private GitHubClient gitHubClient;
    private GitHubProjectsClient gitHubProjectsClient;
    private JiraClient jiraClient;
    private VLLMClient vllmClient;
    private Map<UUID, Long> commandCooldowns;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize cooldown tracker
        commandCooldowns = new HashMap<>();

        // Initialize API clients
        initializeClients();

        // Register commands
        registerCommands();

        getLogger().info("DevCommandsPlugin enabled!");
        getLogger().info("GitHub repo: " + getConfig().getString("github.repository"));
        getLogger().info("vLLM URL: " + getConfig().getString("vllm.url"));
    }

    @Override
    public void onDisable() {
        // Cleanup
        if (gitHubClient != null) {
            gitHubClient.shutdown();
        }
        if (gitHubProjectsClient != null) {
            gitHubProjectsClient.shutdown();
        }
        if (jiraClient != null) {
            jiraClient.shutdown();
        }
        if (vllmClient != null) {
            vllmClient.shutdown();
        }

        getLogger().info("DevCommandsPlugin disabled!");
    }

    private void initializeClients() {
        // Get config values
        String githubToken = resolveConfigValue(getConfig().getString("github.token", ""));
        String githubRepo = getConfig().getString("github.repository", "");
        String githubApiUrl = getConfig().getString("github.api-url", "https://api.github.com");

        String jiraUrl = getConfig().getString("jira.url", "");
        String jiraEmail = resolveConfigValue(getConfig().getString("jira.email", ""));
        String jiraToken = resolveConfigValue(getConfig().getString("jira.api-token", ""));
        String jiraProject = getConfig().getString("jira.project-key", "");

        String vllmUrl = getConfig().getString("vllm.url", "http://localhost:8000");
        String vllmModel = getConfig().getString("vllm.model", "gpt-3.5-turbo");
        int vllmMaxTokens = getConfig().getInt("vllm.max-tokens", 2048);
        double vllmTemperature = getConfig().getDouble("vllm.temperature", 0.7);
        int vllmTimeout = getConfig().getInt("vllm.timeout", 30);

        // Initialize clients
        gitHubClient = new GitHubClient(githubToken, githubRepo, githubApiUrl, getLogger());
        gitHubProjectsClient = new GitHubProjectsClient(githubToken, githubRepo, getLogger());
        jiraClient = new JiraClient(jiraUrl, jiraEmail, jiraToken, jiraProject, getLogger());
        vllmClient = new VLLMClient(vllmUrl, vllmModel, vllmMaxTokens, vllmTemperature, vllmTimeout, getLogger());

        if (getConfig().getBoolean("settings.debug", false)) {
            getLogger().info("Debug mode enabled");
        }
    }

    private void registerCommands() {
        // Git command (handles pr-list, pr-review, kanban, repo, etc.)
        GitCommand gitCommand = new GitCommand(this);
        getCommand("git").setExecutor(gitCommand);
        getCommand("git").setTabCompleter(gitCommand);

        // AI commands
        getCommand("ai-chat").setExecutor(new AIChatCommand(this));
        getCommand("code-explain").setExecutor(new CodeExplainCommand(this));

        // Jira commands
        getCommand("jira-create").setExecutor(new JiraCreateCommand(this));
        getCommand("jira-view").setExecutor(new JiraViewCommand(this));
        getCommand("jira-list").setExecutor(new JiraListCommand(this));
    }

    private String resolveConfigValue(String value) {
        // Resolve environment variables in config
        if (value.startsWith("${") && value.endsWith("}")) {
            String envVar = value.substring(2, value.length() - 1);
            String envValue = System.getenv(envVar);
            return envValue != null ? envValue : "";
        }
        return value;
    }

    public GitHubClient getGitHubClient() {
        return gitHubClient;
    }

    public GitHubProjectsClient getGitHubProjectsClient() {
        return gitHubProjectsClient;
    }

    public JiraClient getJiraClient() {
        return jiraClient;
    }

    public VLLMClient getVLLMClient() {
        return vllmClient;
    }

    public boolean checkCooldown(UUID playerId) {
        int cooldown = getConfig().getInt("settings.command-cooldown", 5);
        if (cooldown <= 0) return true;

        Long lastUse = commandCooldowns.get(playerId);
        long now = System.currentTimeMillis();

        if (lastUse != null && (now - lastUse) < cooldown * 1000L) {
            return false;
        }

        commandCooldowns.put(playerId, now);
        return true;
    }

    public void reinitializeGitHubClients() {
        // Shutdown old clients
        if (gitHubClient != null) {
            gitHubClient.shutdown();
        }
        if (gitHubProjectsClient != null) {
            gitHubProjectsClient.shutdown();
        }

        // Reinitialize with new repository
        String githubToken = resolveConfigValue(getConfig().getString("github.token", ""));
        String githubRepo = getConfig().getString("github.repository", "");
        String githubApiUrl = getConfig().getString("github.api-url", "https://api.github.com");

        gitHubClient = new GitHubClient(githubToken, githubRepo, githubApiUrl, getLogger());
        gitHubProjectsClient = new GitHubProjectsClient(githubToken, githubRepo, getLogger());

        getLogger().info("GitHub clients reinitialized with repo: " + githubRepo);
    }
}
