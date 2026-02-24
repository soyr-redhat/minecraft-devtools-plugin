package com.minecraft.devcommands.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Client for GitHub Projects (Kanban boards) using GraphQL API
 */
public class GitHubProjectsClient {
    private final OkHttpClient httpClient;
    private final String token;
    private final String owner;
    private final String repo;
    private final Gson gson;
    private final Logger logger;

    public GitHubProjectsClient(String token, String repository, Logger logger) {
        this.token = token;
        this.logger = logger;
        this.gson = new Gson();

        // Parse owner/repo
        String[] parts = repository.split("/");
        this.owner = parts[0];
        this.repo = parts[1];

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public List<Project> listProjects() throws IOException {
        String query = String.format("""
            {
              repository(owner: "%s", name: "%s") {
                projectsV2(first: 10) {
                  nodes {
                    id
                    number
                    title
                    url
                  }
                }
              }
            }
            """, owner, repo);

        JsonObject response = executeGraphQL(query);
        JsonArray projects = response.getAsJsonObject("data")
                .getAsJsonObject("repository")
                .getAsJsonObject("projectsV2")
                .getAsJsonArray("nodes");

        List<Project> result = new ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            JsonObject proj = projects.get(i).getAsJsonObject();
            result.add(new Project(
                proj.get("id").getAsString(),
                proj.get("number").getAsInt(),
                proj.get("title").getAsString(),
                proj.get("url").getAsString()
            ));
        }
        return result;
    }

    public ProjectBoard getProjectBoard(int projectNumber) throws IOException {
        // First, get the project ID
        String projectIdQuery = String.format("""
            {
              repository(owner: "%s", name: "%s") {
                projectsV2(first: 10) {
                  nodes {
                    id
                    number
                    title
                  }
                }
              }
            }
            """, owner, repo);

        JsonObject response = executeGraphQL(projectIdQuery);
        JsonArray projects = response.getAsJsonObject("data")
                .getAsJsonObject("repository")
                .getAsJsonObject("projectsV2")
                .getAsJsonArray("nodes");

        String projectId = null;
        String projectTitle = null;
        for (int i = 0; i < projects.size(); i++) {
            JsonObject proj = projects.get(i).getAsJsonObject();
            if (proj.get("number").getAsInt() == projectNumber) {
                projectId = proj.get("id").getAsString();
                projectTitle = proj.get("title").getAsString();
                break;
            }
        }

        if (projectId == null) {
            throw new IOException("Project #" + projectNumber + " not found");
        }

        // Now get project items
        String itemsQuery = String.format("""
            {
              node(id: "%s") {
                ... on ProjectV2 {
                  items(first: 50) {
                    nodes {
                      id
                      content {
                        ... on Issue {
                          number
                          title
                          state
                          url
                        }
                      }
                      fieldValues(first: 8) {
                        nodes {
                          ... on ProjectV2ItemFieldSingleSelectValue {
                            name
                            field {
                              ... on ProjectV2SingleSelectField {
                                name
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """, projectId);

        response = executeGraphQL(itemsQuery);
        JsonObject projectData = response.getAsJsonObject("data").getAsJsonObject("node");
        JsonArray items = projectData.getAsJsonObject("items").getAsJsonArray("nodes");

        List<ProjectItem> projectItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();

            if (!item.has("content") || item.get("content").isJsonNull()) {
                continue;
            }

            JsonObject content = item.getAsJsonObject("content");
            if (!content.has("number")) {
                continue;
            }

            int issueNumber = content.get("number").getAsInt();
            String title = content.get("title").getAsString();
            String state = content.get("state").getAsString();
            String url = content.get("url").getAsString();

            // Get status from field values
            String status = "No Status";
            JsonArray fieldValues = item.getAsJsonObject("fieldValues").getAsJsonArray("nodes");
            for (int j = 0; j < fieldValues.size(); j++) {
                JsonObject fieldValue = fieldValues.get(j).getAsJsonObject();
                if (fieldValue.has("field") && fieldValue.has("name")) {
                    JsonObject field = fieldValue.getAsJsonObject("field");
                    if (field.has("name") && field.get("name").getAsString().equals("Status")) {
                        status = fieldValue.get("name").getAsString();
                        break;
                    }
                }
            }

            projectItems.add(new ProjectItem(issueNumber, title, state, status, url));
        }

        return new ProjectBoard(projectNumber, projectTitle, projectItems);
    }

    public void createIssue(String title, String body, int projectNumber) throws IOException {
        // Create issue using REST API
        String url = String.format("https://api.github.com/repos/%s/%s/issues", owner, repo);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("title", title);
        if (body != null && !body.isEmpty()) {
            requestBody.addProperty("body", body);
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .post(RequestBody.create(
                    gson.toJson(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create issue: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject issue = gson.fromJson(responseBody, JsonObject.class);
            logger.info("Created issue #" + issue.get("number").getAsInt());

            // Note: Adding to project requires project ID and field IDs
            // This is complex and would require additional setup
        }
    }

    private JsonObject executeGraphQL(String query) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("query", query);

        Request request = new Request.Builder()
                .url("https://api.github.com/graphql")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                    gson.toJson(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GraphQL request failed: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            if (json.has("errors")) {
                JsonArray errors = json.getAsJsonArray("errors");
                throw new IOException("GraphQL errors: " + errors.toString());
            }

            return json;
        }
    }

    public void shutdown() {
        // OkHttp manages its own connection pool
    }

    public static class Project {
        public final String id;
        public final int number;
        public final String title;
        public final String url;

        public Project(String id, int number, String title, String url) {
            this.id = id;
            this.number = number;
            this.title = title;
            this.url = url;
        }
    }

    public static class ProjectBoard {
        public final int number;
        public final String title;
        public final List<ProjectItem> items;

        public ProjectBoard(int number, String title, List<ProjectItem> items) {
            this.number = number;
            this.title = title;
            this.items = items;
        }
    }

    public static class ProjectItem {
        public final int issueNumber;
        public final String title;
        public final String state;
        public final String status;
        public final String url;

        public ProjectItem(int issueNumber, String title, String state, String status, String url) {
            this.issueNumber = issueNumber;
            this.title = title;
            this.state = state;
            this.status = status;
            this.url = url;
        }
    }
}
