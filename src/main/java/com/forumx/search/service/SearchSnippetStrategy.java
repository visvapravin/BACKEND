package com.forumx.search.service;

public interface SearchSnippetStrategy {
    String formatSnippet(String text, String query, int maxLength, boolean highlight);
}
