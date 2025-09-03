package com.juca.crawler.dto;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO for {@link com.juca.crawler.domain.CrawledNewsArticle}
 */

@Getter
@Setter
@NoArgsConstructor
public class CrawledNewsArticleDto {
    String articleUrl;
    String media;
    String category;
    String title;
    String htmlContent;
    String author;
    LocalDateTime publishedAt;
    LocalDateTime crawledAt;

}