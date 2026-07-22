package com.forumx.vote.dto.response;
import com.forumx.vote.entity.VoteType;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor; import lombok.AllArgsConstructor;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class VoteResponse { private Long targetId; private String targetType; private VoteType voteType; private Long score; }
