package com.juca.crawler.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ArticleDto {

    private String media;
    private String category;
    private String title;
    private String article;
    private String author;
    private LocalDateTime publishedAt;

    @QueryProjection
    public ArticleDto(String media, String category, String title, String article, String author, LocalDateTime publishedAt) {
        this.media = media;
        this.category = category;
        this.title = title;
        this.article = article;
        this.author = author;
        this.publishedAt = publishedAt;
    }

}
