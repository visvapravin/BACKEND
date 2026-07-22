package com.forumx.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = OpenApiConfiguration.JWT_SECURITY_SCHEME, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", in = SecuritySchemeIn.HEADER)
public class OpenApiConfiguration {

    static final String JWT_SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI forumxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ForumX API")
                        .description("ForumX enterprise API documentation")
                        .version("v1")
                        .contact(new Contact().name("ForumX Team")))
                .components(new Components().addSecuritySchemes(JWT_SECURITY_SCHEME,
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(JWT_SECURITY_SCHEME));
    }

    @Bean
    public OpenApiCustomizer globalResponsesCustomiser() {
        return openApi -> openApi.getPaths().values()
                .forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                    operation.getResponses().addApiResponse("401",
                            new io.swagger.v3.oas.models.responses.ApiResponse().description("Unauthorized"));
                    operation.getResponses().addApiResponse("403",
                            new io.swagger.v3.oas.models.responses.ApiResponse().description("Forbidden"));
                    operation.getResponses().addApiResponse("500",
                            new io.swagger.v3.oas.models.responses.ApiResponse().description("Internal Server Error"));
                }));
    }

    @Bean
    public GroupedOpenApi authApi() {
        return groupedApi("auth", "com.forumx.auth.controller");
    }

    @Bean
    public GroupedOpenApi userApi() {
        return groupedApi("user", "com.forumx.user.controller");
    }

    @Bean
    public GroupedOpenApi questionApi() {
        return groupedApi("question", "com.forumx.question.controller");
    }

    @Bean
    public GroupedOpenApi answerApi() {
        return groupedApi("answer", "com.forumx.answer.controller");
    }

    @Bean
    public GroupedOpenApi commentApi() {
        return groupedApi("comment", "com.forumx.comment.controller");
    }

    @Bean
    public GroupedOpenApi voteApi() {
        return groupedApi("vote", "com.forumx.vote.controller");
    }

    @Bean
    public GroupedOpenApi notificationApi() {
        return groupedApi("notification", "com.forumx.notification.controller");
    }

    @Bean
    public GroupedOpenApi moderationApi() {
        return groupedApi("moderation", "com.forumx.moderation.controller");
    }

    @Bean
    public GroupedOpenApi bookmarkApi() {
        return groupedApi("bookmark", "com.forumx.bookmark.controller");
    }

    @Bean
    public GroupedOpenApi supportApi() {
        return groupedApi("support", "com.forumx.support.ticket.controller");
    }

    @Bean
    public GroupedOpenApi attachmentApi() {
        return groupedApi("attachment", "com.forumx.modules.attachment.controller");
    }

    @Bean
    public GroupedOpenApi analyticsApi() {
        return groupedApi("analytics", "com.forumx.modules.analytics.controller");
    }

    @Bean
    public GroupedOpenApi searchApi() {
        return GroupedOpenApi.builder()
                .group("search")
                .pathsToMatch("/api/v1/search/**")
                .addOpenApiCustomizer(globalResponsesCustomiser())
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return groupedApi("admin", "com.forumx.modules.admin.controller");
    }

    private GroupedOpenApi groupedApi(String groupName, String... packagesToScan) {
        return GroupedOpenApi.builder()
                .group(groupName)
                .packagesToScan(packagesToScan)
                .addOpenApiCustomizer(globalResponsesCustomiser())
                .build();
    }
}
