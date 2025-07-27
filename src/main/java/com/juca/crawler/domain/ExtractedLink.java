package com.juca.crawler.domain;

import com.juca.crawler.dto.ExtractedLinkDto;
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
@Table(name = "extracted_links", uniqueConstraints = { // 여기에 복합 유니크 키를 추가합니다.
        @UniqueConstraint(columnNames = {"source_page_id", "link_url"})
})
public class ExtractedLink extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_page_id", nullable = false)
    private CrawledPage sourcePage;

    @Column(name = "link_url", nullable = false, length = 1000)
    private String linkUrl;

    @Column(name = "link_text", nullable = false, length = 500)
    private String linkText;

    @Column(name = "link_type", nullable = false, length = 50)
    private String linkType;

    @Column(name = "crawled_at")
    private LocalDateTime crawledAt;

    @Builder
    public ExtractedLink(CrawledPage sourcePage, String linkUrl, String linkText, String linkType, LocalDateTime crawledAt) {
        this.sourcePage = sourcePage;
        this.linkUrl = linkUrl;
        this.linkText = linkText;
        this.linkType = linkType;
        this.crawledAt = crawledAt;
    }

    public static ExtractedLink dtoToEntity(ExtractedLinkDto dto, CrawledPage sourcePage) {
        return ExtractedLink.builder()
                .sourcePage(sourcePage)
                .linkUrl(dto.getLinkUrl())
                .linkText(dto.getLinkText())
                .linkType(dto.getLinkType())
                .crawledAt(dto.getCrawledAt())
                .build();
    }
}