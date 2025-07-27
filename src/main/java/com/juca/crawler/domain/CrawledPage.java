package com.juca.crawler.domain;

import com.juca.crawler.dto.CrawledPageDto;
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
@Table(name = "crawled_pages")
public class CrawledPage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(name = "html_content", nullable = false, columnDefinition = "LongText")
    private String htmlContent;

    @Column(name = "title")
    private String title;

    @Column(name = "meta_description", length = 1000)
    private String metaDescription;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "content_type", length = 50)
    private String contentType;

    @Column(name = "crawled_at")
    private LocalDateTime crawledAt;

    @Column(name = "crawl_depth", nullable = false)
    private Integer crawlDepth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_url_id", referencedColumnName = "id")
    private CrawledPage parentPage;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Builder
    public CrawledPage(String url, String domain, String htmlContent, String title, String metaDescription, Integer statusCode,
                       String contentType, LocalDateTime crawledAt, Integer crawlDepth, CrawledPage parentPage, String errorMessage) {
        this.url = url;
        this.domain = domain;
        this.htmlContent = htmlContent;
        this.title = title;
        this.metaDescription = metaDescription;
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.crawledAt = crawledAt;
        this.crawlDepth = crawlDepth;
        this.parentPage = parentPage;
        this.errorMessage = errorMessage;
    }

    public static CrawledPage dtoToEntity(CrawledPageDto dto, CrawledPage parentPage) {
        return CrawledPage.builder()
                .url(dto.getUrl())
                .domain(dto.getDomain())
                .htmlContent(dto.getHtmlContent())
                .title(dto.getTitle())
                .metaDescription(dto.getMetaDescription())
                .statusCode(dto.getStatusCode())
                .contentType(dto.getContentType())
                .crawledAt(dto.getCrawledAt())
                .crawlDepth(dto.getCrawlDepth())
                .parentPage(parentPage)
                .build();
    }
}