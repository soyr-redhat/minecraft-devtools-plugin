package com.minecraft.devcommands.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

public class BookGenerator {
    private static final int MAX_CHARS_PER_PAGE = 256;
    private static final int MAX_LINES_PER_PAGE = 14;

    public static ItemStack createBook(String title, String author, String content, int maxPages) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setTitle(title);
        meta.setAuthor(author);

        // Split content into pages
        List<String> pages = splitIntoPages(content, maxPages);
        meta.setPages(pages);

        book.setItemMeta(meta);
        return book;
    }

    private static List<String> splitIntoPages(String content, int maxPages) {
        List<String> pages = new ArrayList<>();
        String[] lines = content.split("\n");

        StringBuilder currentPage = new StringBuilder();
        int lineCount = 0;

        for (String line : lines) {
            // Handle long lines by wrapping them
            if (line.length() > 40) {
                List<String> wrappedLines = wrapLine(line, 38);
                for (String wrappedLine : wrappedLines) {
                    if (lineCount >= MAX_LINES_PER_PAGE || currentPage.length() + wrappedLine.length() > MAX_CHARS_PER_PAGE) {
                        // Start new page
                        if (currentPage.length() > 0) {
                            pages.add(currentPage.toString());
                            currentPage = new StringBuilder();
                            lineCount = 0;
                        }

                        if (pages.size() >= maxPages) {
                            pages.add("... Content truncated due to page limit ...");
                            return pages;
                        }
                    }

                    currentPage.append(wrappedLine).append("\n");
                    lineCount++;
                }
            } else {
                if (lineCount >= MAX_LINES_PER_PAGE || currentPage.length() + line.length() > MAX_CHARS_PER_PAGE) {
                    // Start new page
                    if (currentPage.length() > 0) {
                        pages.add(currentPage.toString());
                        currentPage = new StringBuilder();
                        lineCount = 0;
                    }

                    if (pages.size() >= maxPages) {
                        pages.add("... Content truncated due to page limit ...");
                        return pages;
                    }
                }

                currentPage.append(line).append("\n");
                lineCount++;
            }
        }

        // Add final page if not empty
        if (currentPage.length() > 0) {
            pages.add(currentPage.toString());
        }

        // Ensure at least one page
        if (pages.isEmpty()) {
            pages.add("Empty content");
        }

        return pages;
    }

    private static List<String> wrapLine(String line, int maxWidth) {
        List<String> wrapped = new ArrayList<>();
        String[] words = line.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxWidth) {
                if (currentLine.length() > 0) {
                    wrapped.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }

                // Handle very long words
                if (word.length() > maxWidth) {
                    wrapped.add(word.substring(0, maxWidth));
                    word = word.substring(maxWidth);
                }
            }

            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }

        if (currentLine.length() > 0) {
            wrapped.add(currentLine.toString());
        }

        return wrapped;
    }
}
