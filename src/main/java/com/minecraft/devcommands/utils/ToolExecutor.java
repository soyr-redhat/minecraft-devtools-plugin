package com.minecraft.devcommands.utils;

import com.google.gson.JsonObject;
import com.minecraft.devcommands.DevCommandsPlugin;
import com.minecraft.devcommands.api.GitHubClient;
import com.minecraft.devcommands.api.JiraClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.entity.Player;

import java.io.IOException;

public class ToolExecutor {
    private final DevCommandsPlugin plugin;
    private final OkHttpClient httpClient;

    public ToolExecutor(DevCommandsPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient();
    }

    public String executeTool(String toolName, JsonObject arguments, Player player) throws Exception {
        switch (toolName) {
            case "git_pr_list":
                return executeGitPrList();

            case "git_pr_review":
                String prNumber = arguments.get("pr_number").getAsString();
                return executeGitPrReview(prNumber);

            case "git_set_repo":
                String repository = arguments.get("repository").getAsString();
                return executeGitSetRepo(repository);

            case "jira_list":
                String filter = arguments.has("filter") ? arguments.get("filter").getAsString() : "";
                return executeJiraList(filter);

            case "jira_view":
                String issueKey = arguments.get("issue_key").getAsString();
                return executeJiraView(issueKey);

            case "jira_create":
                String issueType = arguments.get("issue_type").getAsString();
                String summary = arguments.get("summary").getAsString();
                String description = arguments.has("description") ? arguments.get("description").getAsString() : "";
                return executeJiraCreate(issueType, summary, description);

            case "code_explain":
                String filePath = arguments.get("file_path").getAsString();
                return executeCodeExplain(filePath);

            case "git_list_files":
                String path = arguments.has("path") ? arguments.get("path").getAsString() : "";
                return executeGitListFiles(path);

            case "git_search_code":
                String query = arguments.get("query").getAsString();
                return executeGitSearchCode(query);

            default:
                throw new Exception("Unknown tool: " + toolName);
        }
    }

    private String executeGitPrList() throws Exception {
        GitHubClient github = plugin.getGitHubClient();
        var prs = github.listPullRequests();

        StringBuilder result = new StringBuilder("Open Pull Requests:\n\n");
        for (var pr : prs) {
            result.append(String.format("#%d: %s by %s\n", pr.number, pr.title, pr.author));
        }
        return result.toString();
    }

    private String executeGitPrReview(String prNumber) throws Exception {
        GitHubClient github = plugin.getGitHubClient();

        // Get PR data
        GitHubClient.PullRequest pr;
        if ("latest".equalsIgnoreCase(prNumber)) {
            pr = github.getLatestPullRequest();
        } else {
            int number = Integer.parseInt(prNumber);
            pr = github.getPullRequest(number);
        }

        // Format PR data for AI
        return String.format(
            "PR #%d: %s\nAuthor: %s\nBranch: %s\nState: %s\nCreated: %s\nURL: %s\n\nDescription:\n%s",
            pr.number, pr.title, pr.author, pr.branch, pr.state, pr.createdAt, pr.url, pr.body != null ? pr.body : "(no description)"
        );
    }

    private String executeGitSetRepo(String repository) {
        plugin.getConfig().set("github.repository", repository);
        plugin.saveConfig();
        plugin.reinitializeGitHubClients();
        return "Repository changed to: " + repository;
    }

    private String executeJiraList(String filter) throws Exception {
        JiraClient jira = plugin.getJiraClient();
        String jql;

        switch (filter.toLowerCase()) {
            case "mine":
                jql = "assignee = currentUser() AND resolution = Unresolved ORDER BY updated DESC";
                break;
            case "bugs":
                jql = "type = Bug AND resolution = Unresolved ORDER BY updated DESC";
                break;
            case "all":
                jql = "ORDER BY updated DESC";
                break;
            default:
                jql = "resolution = Unresolved ORDER BY updated DESC";
        }

        var issues = jira.searchIssues(jql, 20);
        StringBuilder result = new StringBuilder("Jira Issues:\n\n");
        for (var issue : issues) {
            result.append(String.format("- %s: %s [%s]\n", issue.key, issue.summary, issue.status));
        }
        return result.toString();
    }

    private String executeJiraView(String issueKey) throws Exception {
        JiraClient jira = plugin.getJiraClient();
        var issue = jira.getIssue(issueKey);

        return String.format(
            "Issue: %s\nSummary: %s\nType: %s\nStatus: %s\nAssignee: %s\n\nDescription:\n%s",
            issue.key, issue.summary, issue.type, issue.status, issue.assignee, issue.description
        );
    }

    private String executeJiraCreate(String issueType, String summary, String description) throws Exception {
        JiraClient jira = plugin.getJiraClient();
        var issue = jira.createIssue(summary, description, issueType);

        return String.format("Created %s: %s\nKey: %s\nURL: %s", issue.type, issue.summary, issue.key, issue.url);
    }

    private String executeCodeExplain(String filePath) throws Exception {
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

        // Truncate if too long
        if (code.length() > 3000) {
            code = code.substring(0, 3000) + "\n... (truncated) ...";
        }

        return String.format("File: %s\n\nCode:\n%s", filePath, code);
    }

    private String executeGitListFiles(String path) throws Exception {
        // Fetch directory listing from GitHub
        String githubToken = resolveConfigValue(plugin.getConfig().getString("github.token", ""));
        String repository = plugin.getConfig().getString("github.repository", "");
        String apiUrl = plugin.getConfig().getString("github.api-url", "https://api.github.com");

        // Clean up path - remove leading/trailing slashes
        path = path.trim();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String url = String.format("%s/repos/%s/contents/%s", apiUrl, repository, path);

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github.v3+json")
            .build();

        String responseBody;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Failed to list files: " + response.code() + " " + response.message());
            }
            responseBody = response.body().string();
        }

        // Parse JSON response
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonArray items = gson.fromJson(responseBody, com.google.gson.JsonArray.class);

        StringBuilder result = new StringBuilder();
        result.append(String.format("Repository: %s\n", repository));
        result.append(String.format("Path: /%s\n\n", path.isEmpty() ? "" : path));
        result.append("Files and Directories:\n\n");

        // Separate directories and files
        java.util.List<String> directories = new java.util.ArrayList<>();
        java.util.List<String> files = new java.util.ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            com.google.gson.JsonObject item = items.get(i).getAsJsonObject();
            String name = item.get("name").getAsString();
            String type = item.get("type").getAsString();
            long size = item.has("size") ? item.get("size").getAsLong() : 0;

            if ("dir".equals(type)) {
                directories.add(name);
            } else {
                // Format file size
                String sizeStr;
                if (size < 1024) {
                    sizeStr = size + "B";
                } else if (size < 1024 * 1024) {
                    sizeStr = (size / 1024) + "KB";
                } else {
                    sizeStr = (size / (1024 * 1024)) + "MB";
                }
                files.add(String.format("%s (%s)", name, sizeStr));
            }
        }

        // List directories first
        if (!directories.isEmpty()) {
            result.append("📁 Directories:\n");
            for (String dir : directories) {
                result.append(String.format("  %s/\n", dir));
            }
            result.append("\n");
        }

        // Then files
        if (!files.isEmpty()) {
            result.append("📄 Files:\n");
            for (String file : files) {
                result.append(String.format("  %s\n", file));
            }
        }

        if (directories.isEmpty() && files.isEmpty()) {
            result.append("(empty directory)\n");
        }

        return result.toString();
    }

    private String executeGitSearchCode(String query) throws Exception {
        // Search code in GitHub repository
        String githubToken = resolveConfigValue(plugin.getConfig().getString("github.token", ""));
        String repository = plugin.getConfig().getString("github.repository", "");
        String apiUrl = plugin.getConfig().getString("github.api-url", "https://api.github.com");

        // Build search query - add repo qualifier
        String searchQuery = java.net.URLEncoder.encode(query + " repo:" + repository, "UTF-8");
        String url = String.format("%s/search/code?q=%s&per_page=15", apiUrl, searchQuery);

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github.v3.text-match+json")
            .build();

        String responseBody;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Failed to search code: " + response.code() + " " + response.message());
            }
            responseBody = response.body().string();
        }

        // Parse JSON response
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonObject jsonResponse = gson.fromJson(responseBody, com.google.gson.JsonObject.class);

        int totalCount = jsonResponse.get("total_count").getAsInt();
        com.google.gson.JsonArray items = jsonResponse.getAsJsonArray("items");

        StringBuilder result = new StringBuilder();
        result.append(String.format("Code Search: '%s'\n", query));
        result.append(String.format("Repository: %s\n", repository));
        result.append(String.format("Found %d match%s\n\n", totalCount, totalCount == 1 ? "" : "es"));

        if (items.size() == 0) {
            result.append("No matches found.\n");
            result.append("\nTry:\n");
            result.append("- Using different keywords\n");
            result.append("- Searching for class/function names\n");
            result.append("- Looking for file extensions (e.g., '.java', '.py')\n");
        } else {
            result.append("Matches:\n\n");

            for (int i = 0; i < Math.min(items.size(), 10); i++) {
                com.google.gson.JsonObject item = items.get(i).getAsJsonObject();
                String name = item.get("name").getAsString();
                String path = item.get("path").getAsString();
                String htmlUrl = item.get("html_url").getAsString();

                result.append(String.format("%d. %s\n", i + 1, path));

                // Get text matches if available
                if (item.has("text_matches") && !item.get("text_matches").isJsonNull()) {
                    com.google.gson.JsonArray textMatches = item.getAsJsonArray("text_matches");
                    if (textMatches.size() > 0) {
                        com.google.gson.JsonObject firstMatch = textMatches.get(0).getAsJsonObject();
                        if (firstMatch.has("fragment")) {
                            String fragment = firstMatch.get("fragment").getAsString();
                            // Show first 150 chars of matching code
                            String preview = fragment.length() > 150
                                ? fragment.substring(0, 150).trim() + "..."
                                : fragment.trim();
                            result.append(String.format("   Preview: %s\n", preview.replaceAll("\n", " ")));
                        }
                    }
                }
                result.append(String.format("   URL: %s\n", htmlUrl));
                result.append("\n");
            }

            if (totalCount > 10) {
                result.append(String.format("... and %d more matches\n", totalCount - 10));
            }
        }

        return result.toString();
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
