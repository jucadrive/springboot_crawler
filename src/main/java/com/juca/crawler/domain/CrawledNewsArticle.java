package com.juca.crawler.domain;

import com.juca.crawler.dto.CrawledNewsArticleDto;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "crawled_news_articles")
public class CrawledNewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "article_url", nullable = false, unique = true)
    private String articleUrl;

    @Column(name = "media", nullable = false, length = 50)
    private String media;

    @Column(name = "category", nullable = false, length = 10)
    private String category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "html_content", nullable = false, columnDefinition = "LongText")
    private String htmlContent;

    @Column(name = "author", length = 10)
    private String author;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "crawled_at", nullable = false)
    private LocalDateTime crawledAt;

    @Builder
    public CrawledNewsArticle(String articleUrl, String media, String category, String title, String htmlContent,
                              String author, LocalDateTime publishedAt, LocalDateTime crawledAt) {
        this.articleUrl = articleUrl;
        this.media = media;
        this.category = category;
        this.title = title;
        this.htmlContent = htmlContent;
        this.author = author;
        this.publishedAt = publishedAt;
        this.crawledAt = crawledAt;
    }

    public static CrawledNewsArticle dtoToEntity(CrawledNewsArticleDto dto) {
        return CrawledNewsArticle.builder()
                .articleUrl(dto.getArticleUrl())
                .media(dto.getMedia())
                .category(dto.getCategory())
                .title(dto.getTitle())
                .htmlContent(dto.getHtmlContent())
                .author(dto.getAuthor())
                .publishedAt(dto.getPublishedAt())
                .crawledAt(dto.getCrawledAt())
                .build();
    }
}