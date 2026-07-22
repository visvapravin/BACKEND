package com.forumx.search.service.impl;

import com.forumx.search.config.SearchProperties;
import com.forumx.search.service.SearchSnippetService;
import com.forumx.search.service.SearchSnippetStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchSnippetServiceImpl implements SearchSnippetService {

    private final SearchSnippetStrategy snippetStrategy;
    private final SearchProperties searchProperties;

    @Override
    public String generateSnippet(String content, String query) {
        return snippetStrategy.formatSnippet(
                content,
                query,
                searchProperties.getMaxSnippetLength(),
                searchProperties.isHighlightMatches()
        );
    }
}
