package com.juca.crawler.repository;

import com.juca.crawler.domain.ExtractedLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedLinkRepository extends JpaRepository<ExtractedLink, Long> {
}