package com.juca.crawler.scheduler;

import com.juca.crawler.service.WebCrawlingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrawlingScheduler {

    @Value("${crawler.base_url}")
    String baseUrl;

    @Value("${crawler.max_depth}")
    int maxDepth;

    private final WebCrawlingService webCrawlingService;

    @Scheduled(fixedDelayString = "#{T(java.util.concurrent.ThreadLocalRandom).current().nextLong(30000, 60000)}") // 5분 ~ 1시간 사이 랜덤 딜레이
    public void startWebCrawling() {
        System.out.println("==========크롤링 스케줄러 시작==========");
        webCrawlingService.startWebCrawling(baseUrl, maxDepth);
        System.out.println("==========크롤링 스케줄러 종료==========");
    }
    
}
