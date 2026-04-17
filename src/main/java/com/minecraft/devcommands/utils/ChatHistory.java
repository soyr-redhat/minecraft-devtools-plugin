package com.minecraft.devcommands.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ChatHistory {
    private static final int MAX_HISTORY = 50;
    private final LinkedList<ChatEntry> history = new LinkedList<>();
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("MM/dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public void addEntry(String playerName, String prompt, String response) {
        history.addFirst(new ChatEntry(playerName, prompt, response, Instant.now()));
        if (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    public List<ChatEntry> getRecentHistory(int count) {
        return new ArrayList<>(history.subList(0, Math.min(count, history.size())));
    }

    public int size() {
        return history.size();
    }

    public static class ChatEntry {
        public final String playerName;
        public final String prompt;
        public final String response;
        public final Instant timestamp;

        public ChatEntry(String playerName, String prompt, String response, Instant timestamp) {
            this.playerName = playerName;
            this.prompt = prompt;
            this.response = response;
            this.timestamp = timestamp;
        }

        public String getFormattedTime() {
            return TIME_FORMATTER.format(timestamp);
        }

        public String getShortPrompt(int maxLength) {
            if (prompt.length() <= maxLength) {
                return prompt;
            }
            return prompt.substring(0, maxLength - 3) + "...";
        }
    }
}
