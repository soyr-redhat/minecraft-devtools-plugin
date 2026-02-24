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
    private final Gson gson;
    private final Logger logger;

    public VLLMClient(String url, String model, int maxTokens, double temperature, int timeout, Logger logger) {
        this.url = url;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
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

        Request request = new Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Content-Type", "application/json")
            .build();

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

        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                // Fall back to completions endpoint
                logger.warning("Chat endpoint failed, falling back to completions");
                return complete(message);
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            // Parse chat response
            if (json.has("choices")) {
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (choice.has("message")) {
                        JsonObject msg = choice.getAsJsonObject("message");
                        if (msg.has("content")) {
                            return msg.get("content").getAsString().trim();
                        }
                    }
                }
            }

            throw new IOException("Unexpected chat response format from vLLM");
        }
    }

    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
