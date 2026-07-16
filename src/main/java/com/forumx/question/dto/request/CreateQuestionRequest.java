package com.forumx.question.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload representing the request to create a new question.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionRequest {

    /**
     * The title of the question. Must not be blank.
     */
    @NotBlank
    @Size(max = 255)
    private String title;

    /**
     * The content body of the question. Must not be blank.
     */
    @NotBlank
    @Size(max = 10000)
    private String content;
}
