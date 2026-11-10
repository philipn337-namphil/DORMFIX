package com.dormfix.maintenance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Comment payload. Visibility is required and has no default, so an internal remark cannot become
 * public through an omitted field. The author is always the JWT subject.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AddCommentRequest(@NotBlank @Size(max = 2000) String content,
        @NotBlank String visibility) { }
