package com.juca.crawler.repository;

import com.juca.crawler.domain.CrawledPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrawledPageRepository extends JpaRepository<CrawledPage, Long> {
    Optional<CrawledPage> findByUrl(String url);
}