package com.minecraft.devcommands.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ToolDefinitions {

    public static JsonArray getMinecraftTools() {
        JsonArray tools = new JsonArray();

        // Git PR List tool
        tools.add(createTool(
            "git_pr_list",
            "List all open pull requests from the configured GitHub repository",
            new JsonObject()
        ));

        // Git PR Review tool
        JsonObject prReviewParams = new JsonObject();
        addStringProperty(prReviewParams, "pr_number", "PR number or 'latest' for most recent", true);
        tools.add(createTool(
            "git_pr_review",
            "Get an AI-powered review of a pull request",
            prReviewParams
        ));

        // Git Set Repo tool
        JsonObject setRepoParams = new JsonObject();
        addStringProperty(setRepoParams, "repository", "Repository in format 'owner/repo'", true);
        tools.add(createTool(
            "git_set_repo",
            "Change the GitHub repository being queried",
            setRepoParams
        ));

        // Jira List tool
        JsonObject jiraListParams = new JsonObject();
        addEnumProperty(jiraListParams, "filter", "Filter type", new String[]{"mine", "bugs", "all", ""}, false);
        tools.add(createTool(
            "jira_list",
            "List Jira issues with optional filter",
            jiraListParams
        ));

        // Jira View tool
        JsonObject jiraViewParams = new JsonObject();
        addStringProperty(jiraViewParams, "issue_key", "Jira issue key (e.g., PROJ-123)", true);
        tools.add(createTool(
            "jira_view",
            "View details of a specific Jira issue",
            jiraViewParams
        ));

        // Jira Create tool
        JsonObject jiraCreateParams = new JsonObject();
        addEnumProperty(jiraCreateParams, "issue_type", "Type of issue", new String[]{"Bug", "Task", "Story"}, true);
        addStringProperty(jiraCreateParams, "summary", "Brief summary of the issue", true);
        addStringProperty(jiraCreateParams, "description", "Detailed description", false);
        tools.add(createTool(
            "jira_create",
            "Create a new Jira issue",
            jiraCreateParams
        ));

        // Code Explain tool
        JsonObject codeExplainParams = new JsonObject();
        addStringProperty(codeExplainParams, "file_path", "Path to the file in the repository", true);
        tools.add(createTool(
            "code_explain",
            "Get AI explanation of code from a file",
            codeExplainParams
        ));

        // Git List Files tool
        JsonObject listFilesParams = new JsonObject();
        addStringProperty(listFilesParams, "path", "Directory path to list (empty for root, e.g., 'src/main')", false);
        tools.add(createTool(
            "git_list_files",
            "List files and directories in the repository. Use to explore repo structure.",
            listFilesParams
        ));

        // Git Search Code tool
        JsonObject searchCodeParams = new JsonObject();
        addStringProperty(searchCodeParams, "query", "Search query (e.g., 'function login', 'class AuthToken', 'import pandas')", true);
        tools.add(createTool(
            "git_search_code",
            "Search for code, functions, classes, or keywords across all files in the repository.",
            searchCodeParams
        ));

        return tools;
    }

    private static JsonObject createTool(String name, String description, JsonObject parameters) {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);

        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.add("properties", parameters);

        // Add required fields
        JsonArray required = new JsonArray();
        if (parameters.has("required")) {
            required = parameters.getAsJsonArray("required");
            parameters.remove("required");
        }
        params.add("required", required);

        function.add("parameters", params);
        tool.add("function", function);

        return tool;
    }

    private static void addStringProperty(JsonObject params, String name, String description, boolean required) {
        if (!params.has("properties")) {
            params.add("properties", new JsonObject());
        }
        if (!params.has("required")) {
            params.add("required", new JsonArray());
        }

        JsonObject properties = params.getAsJsonObject("properties");
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        properties.add(name, property);

        if (required) {
            params.getAsJsonArray("required").add(name);
        }
    }

    private static void addEnumProperty(JsonObject params, String name, String description, String[] enumValues, boolean required) {
        if (!params.has("properties")) {
            params.add("properties", new JsonObject());
        }
        if (!params.has("required")) {
            params.add("required", new JsonArray());
        }

        JsonObject properties = params.getAsJsonObject("properties");
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);

        JsonArray enumArray = new JsonArray();
        for (String value : enumValues) {
            enumArray.add(value);
        }
        property.add("enum", enumArray);

        properties.add(name, property);

        if (required) {
            params.getAsJsonArray("required").add(name);
        }
    }
}
