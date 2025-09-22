package com.juca.crawler.repository;

import com.juca.crawler.dto.ArticleDto;

public interface CrawledNewsArticleCustomRepository {

    ArticleDto findByArticleId(Long id);
}
