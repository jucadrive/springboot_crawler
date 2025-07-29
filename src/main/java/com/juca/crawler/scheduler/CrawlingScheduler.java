package com.juca.crawler.scheduler;

import com.juca.crawler.service.WebCrawlingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrawlingScheduler {

    @Value("${crawler.base_web_url}")
    String baseUrl;

    @Value("${crawler.base_stock_price_url}")
    String baseStockPriceUrl;

    @Value("${crawler.max_depth}")
    int maxDepth;

    private final WebCrawlingService webCrawlingService;

//    @Scheduled(fixedDelayString = "#{T(java.util.concurrent.ThreadLocalRandom).current().nextLong(30000, 60000)}") // 5분 ~ 1시간 사이 랜덤 딜레이
//    public void startWebCrawling() {
//        System.out.println("==========크롤링 스케줄러 시작==========");
//        webCrawlingService.startWebCrawling(baseUrl, maxDepth);
//        System.out.println("==========크롤링 스케줄러 종료==========");
//    }

    /**
     * 2분(120초)마다 작동하는 스케줄러
     * 월요일부터 금요일, 오전 9시 0분부터 오후 5시 58분까지 (5시 59분에 시작하면 다음 2분 후는 5시를 넘어가므로)
     */
    @Scheduled(cron = "0 0/2 9-17 ? * MON-FRI") // 매 2분마다, 9시부터 17시까지, 월-금 삼성전자 주식 정보 수집
    public void startFinanceCrawling1() {
        String url = baseStockPriceUrl + "005930";
        webCrawlingService.stockPriceCrawling(url, maxDepth);
    }

    /**
     * 2분 30초(150초)마다 작동하는 스케줄러
     * 월요일부터 금요일, 오전 9시 0분부터 오후 5시 57분 30초까지
     */
    @Scheduled(cron = "30 0/2 9-17 ? * MON-FRI") // 매 2분마다 30초에 시작, 9시부터 17시까지, 월-금 SK하이닉스 주식 정보 수집
    public void startFinanceCrawling2() {
        String url = baseStockPriceUrl + "000660";
        webCrawlingService.stockPriceCrawling(url, maxDepth);
    }

    /**
     * 2분 45초(165초)마다 작동하는 스케줄러
     * 월요일부터 금요일, 오전 9시 0분부터 오후 5시 57분 15초까지
     */
    @Scheduled(cron = "45 0/2 9-17 ? * MON-FRI") // 매 2분마다 45초에 시작, 9시부터 17시까지, 월-금 현대자동차 주식 정보 수집
    public void startFinanceCrawling3() {
        String url = baseStockPriceUrl + "005380";
        webCrawlingService.stockPriceCrawling(url, maxDepth);
    }
}
