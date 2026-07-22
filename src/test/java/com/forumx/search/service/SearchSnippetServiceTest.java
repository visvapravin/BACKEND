package com.forumx.search.service;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.search.config.SearchProperties;
import com.forumx.search.service.impl.DefaultSnippetStrategy;
import com.forumx.search.service.impl.SearchSnippetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SearchSnippetServiceTest {

    private SearchSnippetService snippetService;

    @BeforeEach
    public void setUp() {
        SearchProperties properties = new SearchProperties();
        properties.setMaxSnippetLength(50);
        properties.setHighlightMatches(true);

        SearchSnippetStrategy strategy = new DefaultSnippetStrategy();
        snippetService = new SearchSnippetServiceImpl(strategy, properties);
    }

    @Test
    public void testGenerateSnippetHighlightsQuery() {
        String content = "Spring Boot makes building production ready web apps very easy.";
        String query = "production";

        String snippet = snippetService.generateSnippet(content, query);

        assertNotNull(snippet);
        assertTrue(snippet.contains("<mark>production</mark>"));
    }

    @Test
    public void testGenerateSnippetTruncatesLongContent() {
        String longContent = "This is a very long text content designed to test whether text snippet service properly truncates long text payloads down to specified max length.";
        String query = "payloads";

        String snippet = snippetService.generateSnippet(longContent, query);

        assertNotNull(snippet);
        assertTrue(snippet.length() <= 80); // 50 char limit + mark tags + ellipses
        assertTrue(snippet.contains("<mark>payloads</mark>"));
    }

    @Test
    public void testGenerateSnippetEmptyContentReturnsEmpty() {
        assertEquals("", snippetService.generateSnippet(null, "query"));
        assertEquals("", snippetService.generateSnippet(" ", "query"));
    }
}
