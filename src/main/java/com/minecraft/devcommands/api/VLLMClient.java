package com.minecraft.devcommands.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class VLLMClient {
    private final OkHttpClient client;
    private final String url;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final String apiToken;
    private final Gson gson;
    private final Logger logger;

    public VLLMClient(String url, String model, int maxTokens, double temperature, int timeout, String apiToken, Logger logger) {
        this.url = url;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.apiToken = apiToken;
        this.logger = logger;
        this.gson = new Gson();

        this.client = new OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build();
    }

    public String complete(String prompt) throws IOException {
        // Try OpenAI-compatible API format first
        String endpoint = url.endsWith("/") ? url + "v1/completions" : url + "/v1/completions";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("max_tokens", maxTokens);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("stream", false);

        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.parse("application/json")
        );

        Request.Builder requestBuilder = new Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Content-Type", "application/json");

        // Add authorization header if API token is provided
        if (apiToken != null && !apiToken.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiToken);
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new IOException("vLLM request failed: " + response.code() + " " + response.message() + "\n" + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            // Parse OpenAI-compatible response
            if (json.has("choices")) {
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (choice.has("text")) {
                        return choice.get("text").getAsString().trim();
                    }
                }
            }

            throw new IOException("Unexpected response format from vLLM");
        }
    }

    public String chat(String message) throws IOException {
        return chat(message, null);
    }

    public ChatResponse chatWithTools(String message, JsonArray tools) throws IOException {
        // Try chat completions endpoint
        String endpoint = url.endsWith("/") ? url + "v1/chat/completions" : url + "/v1/chat/completions";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", maxTokens);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", message);
        messages.add(userMessage);
        requestBody.add("messages", messages);

        // Add tools if provided
        if (tools != null && tools.size() > 0) {
            requestBody.add("tools", tools);
            requestBody.addProperty("tool_choice", "auto");
        }

        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.parse("application/json")
        );

        Request.Builder requestBuilder = new Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Content-Type", "application/json");

        // Add authorization header if API token is provided
        if (apiToken != null && !apiToken.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiToken);
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new IOException("Chat request failed: " + response.code() + "\n" + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            return parseChatResponse(json);
        }
    }

    public String chat(String message, JsonArray tools) throws IOException {
        ChatResponse response = chatWithTools(message, tools);
        return response.content != null ? response.content : "";
    }

    private ChatResponse parseChatResponse(JsonObject json) throws IOException {
        if (!json.has("choices")) {
            throw new IOException("No choices in response");
        }

        JsonArray choices = json.getAsJsonArray("choices");
        if (choices.size() == 0) {
            throw new IOException("Empty choices array");
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!choice.has("message")) {
            throw new IOException("No message in choice");
        }

        JsonObject message = choice.getAsJsonObject("message");
        ChatResponse response = new ChatResponse();

        // Get content if present
        if (message.has("content") && !message.get("content").isJsonNull()) {
            response.content = message.get("content").getAsString().trim();
        }

        // Get tool calls if present
        if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
            response.toolCalls = message.getAsJsonArray("tool_calls");
        }

        return response;
    }

    public static class ChatResponse {
        public String content;
        public JsonArray toolCalls;

        public boolean hasToolCalls() {
            return toolCalls != null && toolCalls.size() > 0;
        }
    }

    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
