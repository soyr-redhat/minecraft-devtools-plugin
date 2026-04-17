package com.minecraft.devcommands.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class JiraClient {
    private final OkHttpClient httpClient;
    private final String jiraUrl;
    private final String email;
    private final String apiToken;
    private final String projectKey;
    private final Gson gson;
    private final Logger logger;

    public JiraClient(String jiraUrl, String email, String apiToken, String projectKey, Logger logger) {
        this.jiraUrl = jiraUrl.endsWith("/") ? jiraUrl.substring(0, jiraUrl.length() - 1) : jiraUrl;
        this.email = email;
        this.apiToken = apiToken;
        this.projectKey = projectKey;
        this.logger = logger;
        this.gson = new Gson();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private String getAuthHeader() {
        return Credentials.basic(email, apiToken);
    }

    public Issue getIssue(String issueKey) throws IOException {
        String url = jiraUrl + "/rest/api/3/issue/" + issueKey;

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", getAuthHeader())
                .header("Accept", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch issue: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            return parseIssue(json);
        }
    }

    public List<Issue> searchIssues(String jql, int maxResults) throws IOException {
        String url = jiraUrl + "/rest/api/3/search";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("jql", jql);
        requestBody.addProperty("maxResults", maxResults);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", getAuthHeader())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                    gson.toJson(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to search issues: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            JsonArray issues = json.getAsJsonArray("issues");

            List<Issue> result = new ArrayList<>();
            for (int i = 0; i < issues.size(); i++) {
                result.add(parseIssue(issues.get(i).getAsJsonObject()));
            }
            return result;
        }
    }

    public Issue createIssue(String summary, String description, String issueType) throws IOException {
        String url = jiraUrl + "/rest/api/3/issue";

        JsonObject fields = new JsonObject();

        JsonObject project = new JsonObject();
        project.addProperty("key", projectKey);
        fields.add("project", project);

        fields.addProperty("summary", summary);

        JsonObject type = new JsonObject();
        type.addProperty("name", issueType);
        fields.add("issuetype", type);

        if (description != null && !description.isEmpty()) {
            JsonObject descriptionContent = new JsonObject();
            descriptionContent.addProperty("type", "doc");
            descriptionContent.addProperty("version", 1);

            JsonArray content = new JsonArray();
            JsonObject paragraph = new JsonObject();
            paragraph.addProperty("type", "paragraph");

            JsonArray paragraphContent = new JsonArray();
            JsonObject text = new JsonObject();
            text.addProperty("type", "text");
            text.addProperty("text", description);
            paragraphContent.add(text);

            paragraph.add("content", paragraphContent);
            content.add(paragraph);
            descriptionContent.add("content", content);

            fields.add("description", descriptionContent);
        }

        JsonObject requestBody = new JsonObject();
        requestBody.add("fields", fields);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", getAuthHeader())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                    gson.toJson(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("Failed to create issue: " + response.code() + " - " + error);
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            String key = json.get("key").getAsString();

            // Fetch the created issue to get full details
            return getIssue(key);
        }
    }

    public void updateIssueStatus(String issueKey, String transitionName) throws IOException {
        // First, get available transitions
        String transitionsUrl = jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions";

        Request getTransitions = new Request.Builder()
                .url(transitionsUrl)
                .header("Authorization", getAuthHeader())
                .build();

        String transitionId = null;
        try (Response response = httpClient.newCall(getTransitions).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get transitions: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            JsonArray transitions = json.getAsJsonArray("transitions");

            for (int i = 0; i < transitions.size(); i++) {
                JsonObject transition = transitions.get(i).getAsJsonObject();
                String name = transition.get("name").getAsString();
                if (name.equalsIgnoreCase(transitionName)) {
                    transitionId = transition.get("id").getAsString();
                    break;
                }
            }
        }

        if (transitionId == null) {
            throw new IOException("Transition '" + transitionName + "' not found for issue " + issueKey);
        }

        // Now perform the transition
        JsonObject transitionObj = new JsonObject();
        transitionObj.addProperty("id", transitionId);

        JsonObject requestBody = new JsonObject();
        requestBody.add("transition", transitionObj);

        Request request = new Request.Builder()
                .url(transitionsUrl)
                .header("Authorization", getAuthHeader())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                    gson.toJson(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to transition issue: " + response.code());
            }
        }
    }

    public void addComment(String issueKey, String comment) throws IOException {
        String url = jiraUrl + "/rest/api/3/issue/" + issueKey + "/comment";

        JsonObject body = new JsonObject();
        body.addProperty("type", "doc");
        body.addProperty("version", 1);

        JsonArray content = new JsonArray();
        JsonObject paragraph = new JsonObject();
        paragraph.addProperty("type", "paragraph");

        JsonArray paragraphContent = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", comment);
        paragraphContent.add(text);

        paragraph.add("content", paragraphContent);
        content.add(paragraph);
        body.add("content", content);

        JsonObject requestBody = new JsonObject();
        requestBody.add("body", body);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", getAuthHeader())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                    gson.toJson(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to add comment: " + response.code());
            }
        }
    }

    private Issue parseIssue(JsonObject json) {
        String key = json.get("key").getAsString();
        JsonObject fields = json.getAsJsonObject("fields");

        String summary = fields.get("summary").getAsString();
        String description = "";
        if (fields.has("description") && !fields.get("description").isJsonNull()) {
            description = extractTextFromADF(fields.getAsJsonObject("description"));
        }

        String status = fields.getAsJsonObject("status").get("name").getAsString();
        String issueType = fields.getAsJsonObject("issuetype").get("name").getAsString();

        String assignee = "Unassigned";
        if (fields.has("assignee") && !fields.get("assignee").isJsonNull()) {
            assignee = fields.getAsJsonObject("assignee").get("displayName").getAsString();
        }

        String url = jiraUrl + "/browse/" + key;

        return new Issue(key, summary, description, status, issueType, assignee, url);
    }

    private String extractTextFromADF(JsonObject adf) {
        // Simple ADF (Atlassian Document Format) text extractor
        StringBuilder text = new StringBuilder();
        if (adf.has("content")) {
            JsonArray content = adf.getAsJsonArray("content");
            for (int i = 0; i < content.size(); i++) {
                JsonObject node = content.get(i).getAsJsonObject();
                if (node.has("content")) {
                    JsonArray innerContent = node.getAsJsonArray("content");
                    for (int j = 0; j < innerContent.size(); j++) {
                        JsonObject textNode = innerContent.get(j).getAsJsonObject();
                        if (textNode.has("text")) {
                            text.append(textNode.get("text").getAsString());
                        }
                    }
                }
                text.append("\n");
            }
        }
        return text.toString().trim();
    }

    public void shutdown() {
        // OkHttp manages its own connection pool
    }

    public static class Issue {
        public final String key;
        public final String summary;
        public final String description;
        public final String status;
        public final String type;
        public final String assignee;
        public final String url;

        public Issue(String key, String summary, String description, String status,
                    String type, String assignee, String url) {
            this.key = key;
            this.summary = summary;
            this.description = description;
            this.status = status;
            this.type = type;
            this.assignee = assignee;
            this.url = url;
        }
    }
}
