package com.juca.crawler.domain;

import com.juca.crawler.dto.CnnArticleDto;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "cnn_articles")
public class CnnArticle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "title_kr")
    private String titleKr;

    @Column(name = "article_url", nullable = false, length = 1000)
    private String articleUrl;

    @Column(name = "content", nullable = false, columnDefinition = "LongText")
    private String content;

    @Column(name = "content_kr", columnDefinition = "LongText")
    private String contentKr;

    @Column(name = "author", length = 100)
    private String author;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "crawled_at", nullable = false)
    private LocalDateTime crawledAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "translated_at")
    private LocalDateTime translatedAt;

    @Builder
    public CnnArticle(String title, String titleKr, String articleUrl, String content,
                      String contentKr, String author, Integer statusCode, String errorMessage,
                      LocalDateTime crawledAt, LocalDateTime publishedAt, LocalDateTime translatedAt) {
        this.title = title;
        this.titleKr = titleKr;
        this.articleUrl = articleUrl;
        this.content = content;
        this.contentKr = contentKr;
        this.author = author;
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
        this.crawledAt = crawledAt;
        this.publishedAt = publishedAt;
        this.translatedAt = translatedAt;
    }

    public static CnnArticle toEntity(CnnArticleDto dto) {
        return CnnArticle.builder()
                .title(dto.getTitle())
                .titleKr(dto.getTitleKr())
                .articleUrl(dto.getArticleUrl())
                .content(dto.getContent())
                .contentKr(dto.getContentKr())
                .author(dto.getAuthor())
                .statusCode(dto.getStatusCode())
                .errorMessage(dto.getErrorMessage())
                .crawledAt(dto.getCrawledAt())
                .publishedAt(dto.getPublishedAt())
                .translatedAt(dto.getTranslatedAt())
                .build();
    }
}