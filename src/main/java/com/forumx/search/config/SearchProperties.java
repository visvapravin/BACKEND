package com.forumx.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "forumx.search")
public class SearchProperties {

    private String provider = "POSTGRES";
    private String language = "english";
    private int maxSnippetLength = 200;
    private boolean highlightMatches = true;

    private Weights weights = new Weights();

    @Data
    public static class Weights {
        private String title = "A";
        private String content = "B";
        private String username = "A";
    }
}
