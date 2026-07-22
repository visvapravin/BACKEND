package com.forumx.moderation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class ModerationTarget implements Serializable {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 50, nullable = false)
    private ModerationTargetType targetType;

    @NotNull
    @Column(name = "target_id", nullable = false)
    private Long targetId;
}
