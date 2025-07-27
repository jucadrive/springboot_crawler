package com.juca.crawler.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO for {@link com.juca.crawler.domain.CrawledPage}
 */
@Getter
@Setter
@NoArgsConstructor
public class CrawledPageDto  {
    private String url;
    private String domain;
    private String htmlContent;
    private String title;
    private String metaDescription;
    private Integer statusCode;
    private String contentType;
    private LocalDateTime crawledAt;
    private Integer crawlDepth;
    private Long parentPageId;
    private String errorMessage;
}