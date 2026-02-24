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

public class GitHubClient {
    private final OkHttpClient client;
    private final String token;
    private final String repository;
    private final String apiUrl;
    private final Gson gson;
    private final Logger logger;

    public GitHubClient(String token, String repository, String apiUrl, Logger logger) {
        this.token = token;
        this.repository = repository;
        this.apiUrl = apiUrl;
        this.logger = logger;
        this.gson = new Gson();

        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    public PullRequest getPullRequest(int number) throws IOException {
        String url = String.format("%s/repos/%s/pulls/%d", apiUrl, repository, number);

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch PR: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);

            return parsePullRequest(json);
        }
    }

    public PullRequest getLatestPullRequest() throws IOException {
        List<PullRequest> prs = listPullRequests();
        if (prs.isEmpty()) {
            throw new IOException("No open pull requests found");
        }
        return prs.get(0);
    }

    public List<PullRequest> listPullRequests() throws IOException {
        String url = String.format("%s/repos/%s/pulls?state=open&sort=created&direction=desc",
            apiUrl, repository);

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to list PRs: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonArray array = gson.fromJson(body, JsonArray.class);

            List<PullRequest> prs = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                prs.add(parsePullRequest(array.get(i).getAsJsonObject()));
            }

            return prs;
        }
    }

    public String getPullRequestDiff(int number) throws IOException {
        String url = String.format("%s/repos/%s/pulls/%d", apiUrl, repository, number);

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3.diff")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch diff: " + response.code() + " " + response.message());
            }

            return response.body().string();
        }
    }

    private PullRequest parsePullRequest(JsonObject json) {
        PullRequest pr = new PullRequest();
        pr.number = json.get("number").getAsInt();
        pr.title = json.get("title").getAsString();
        pr.body = json.has("body") && !json.get("body").isJsonNull()
            ? json.get("body").getAsString() : "";
        pr.state = json.get("state").getAsString();
        pr.author = json.getAsJsonObject("user").get("login").getAsString();
        pr.url = json.get("html_url").getAsString();
        pr.createdAt = json.get("created_at").getAsString();

        if (json.has("head") && json.getAsJsonObject("head").has("ref")) {
            pr.branch = json.getAsJsonObject("head").get("ref").getAsString();
        }

        return pr;
    }

    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    public static class PullRequest {
        public int number;
        public String title;
        public String body;
        public String state;
        public String author;
        public String url;
        public String branch;
        public String createdAt;

        @Override
        public String toString() {
            return String.format("PR #%d: %s\nAuthor: %s\nBranch: %s\nState: %s",
                number, title, author, branch, state);
        }
    }
}
