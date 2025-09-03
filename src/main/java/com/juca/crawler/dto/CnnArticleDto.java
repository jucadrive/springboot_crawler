package com.juca.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO for {@link com.juca.crawler.domain.CnnArticle}
 */
@Getter
@Setter
@NoArgsConstructor
public class CnnArticleDto {
    private String title;
    private String titleKr;
    private String articleUrl;
    private String content;
    private String contentKr;
    private String author;
    private Integer statusCode;
    private String errorMessage;
    private LocalDateTime crawledAt;
    private LocalDateTime publishedAt;
    private LocalDateTime translatedAt;
}