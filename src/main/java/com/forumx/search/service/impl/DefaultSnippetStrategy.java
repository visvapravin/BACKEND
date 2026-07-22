package com.forumx.search.service.impl;

import com.forumx.search.service.SearchSnippetStrategy;
import org.springframework.stereotype.Component;

@Component
public class DefaultSnippetStrategy implements SearchSnippetStrategy {

    @Override
    public String formatSnippet(String text, String query, int maxLength, boolean highlight) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLength) {
            return applyHighlight(cleaned, query, highlight);
        }

        int queryIndex = -1;
        if (query != null && !query.isBlank()) {
            queryIndex = cleaned.toLowerCase().indexOf(query.toLowerCase());
        }

        if (queryIndex == -1) {
            String truncated = cleaned.substring(0, maxLength) + "...";
            return applyHighlight(truncated, query, highlight);
        }

        int start = Math.max(0, queryIndex - (maxLength / 2));
        int end = Math.min(cleaned.length(), start + maxLength);

        String prefix = start > 0 ? "..." : "";
        String suffix = end < cleaned.length() ? "..." : "";

        String excerpt = prefix + cleaned.substring(start, end) + suffix;
        return applyHighlight(excerpt, query, highlight);
    }

    private String applyHighlight(String text, String query, boolean highlight) {
        if (!highlight || query == null || query.isBlank()) {
            return text;
        }
        // Match case-insensitively and wrap with <mark> tags
        String regex = "(?i)(" + java.util.regex.Pattern.quote(query) + ")";
        return text.replaceAll(regex, "<mark>$1</mark>");
    }
}
