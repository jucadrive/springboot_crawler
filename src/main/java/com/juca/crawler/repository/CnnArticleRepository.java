package com.juca.crawler.repository;

import com.juca.crawler.domain.CnnArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CnnArticleRepository extends JpaRepository<CnnArticle, Long> {
    Optional<CnnArticle> findByArticleUrl(String articleUrl);
}