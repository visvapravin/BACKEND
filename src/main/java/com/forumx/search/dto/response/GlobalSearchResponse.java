package com.forumx.search.dto.response;

import com.forumx.search.domain.AnswerSearchResult;
import com.forumx.search.domain.QuestionSearchResult;
import com.forumx.search.domain.UserSearchResult;
import java.util.List;

public record GlobalSearchResponse(
        List<QuestionSearchResult> questions,
        List<AnswerSearchResult> answers,
        List<UserSearchResult> users,
        SearchSummary summary
) {
}
