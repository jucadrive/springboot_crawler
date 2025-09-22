package com.juca.crawler.repository;

import com.juca.crawler.domain.CrawledNewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrawledNewsArticleRepository extends JpaRepository<CrawledNewsArticle, Long>, CrawledNewsArticleCustomRepository {

    Optional<CrawledNewsArticle> findByArticleUrl(String articleUrl);
}