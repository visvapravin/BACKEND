package com.forumx.vote.controller;

import com.forumx.vote.dto.request.CreateVoteRequest;
import com.forumx.vote.dto.response.ScoreResponse;
import com.forumx.vote.dto.response.VoteResponse;
import com.forumx.vote.service.VoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated @RestController @RequiredArgsConstructor @RequestMapping("/api/v1")
@Tag(name = "Votes", description = "Community voting operations")
public class VoteController {
    private final VoteService voteService;
    @PostMapping("/questions/{id}/vote") public ResponseEntity<VoteResponse> voteQuestion(@PathVariable Long id, @Valid @RequestBody CreateVoteRequest request) { return ResponseEntity.ok(voteService.voteQuestion(id, request)); }
    @PostMapping("/answers/{id}/vote") public ResponseEntity<VoteResponse> voteAnswer(@PathVariable Long id, @Valid @RequestBody CreateVoteRequest request) { return ResponseEntity.ok(voteService.voteAnswer(id, request)); }
    @PostMapping("/comments/{id}/vote") public ResponseEntity<VoteResponse> voteComment(@PathVariable Long id, @Valid @RequestBody CreateVoteRequest request) { return ResponseEntity.ok(voteService.voteComment(id, request)); }
    @GetMapping("/questions/{id}/score") public ResponseEntity<ScoreResponse> questionScore(@PathVariable Long id) { return ResponseEntity.ok(voteService.getQuestionScore(id)); }
    @GetMapping("/answers/{id}/score") public ResponseEntity<ScoreResponse> answerScore(@PathVariable Long id) { return ResponseEntity.ok(voteService.getAnswerScore(id)); }
    @GetMapping("/comments/{id}/score") public ResponseEntity<ScoreResponse> commentScore(@PathVariable Long id) { return ResponseEntity.ok(voteService.getCommentScore(id)); }
}
