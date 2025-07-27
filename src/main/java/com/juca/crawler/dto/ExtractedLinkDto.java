package com.juca.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO for {@link com.juca.crawler.domain.ExtractedLink}
 */

@Getter
@Setter
@NoArgsConstructor
public class ExtractedLinkDto {
    private Long sourcePageId;
    private String linkUrl;
    private String linkText;
    private String linkType;
    private LocalDateTime crawledAt;
}