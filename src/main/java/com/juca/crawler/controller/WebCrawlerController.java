package com.juca.crawler.controller;

import com.juca.crawler.service.WebCrawlingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WebCrawlerController {

    private final WebCrawlingService webCrawlingService;

    @Value("${crawler.base_naver_news_url}")
    String url;

    @PostMapping("/news")
    public void naverNewsCrawler() {
        webCrawlingService.naverNewsCrawling(url, 0);
    }
}
