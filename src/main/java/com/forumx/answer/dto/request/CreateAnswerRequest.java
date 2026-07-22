package com.forumx.answer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload representing the request to create a new answer.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAnswerRequest {

    /**
     * The content body of the answer. Must not be blank.
     */
    @NotBlank
    @Size(min = 10, max = 10000)
    private String content;
}
