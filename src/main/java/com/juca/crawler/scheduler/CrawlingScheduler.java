package com.juca.crawler.scheduler;

import com.juca.crawler.service.WebCrawlingService;
import com.juca.crawler.util.LogUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CrawlingScheduler {

    @Value("${crawler.base_web_url}")
    String baseUrl;

    @Value("${crawler.base_stock_price_url}")
    String baseStockPriceUrl;

    @Value("${crawler.base_article_url}")
    String baseArticleUrl;

    @Value("${crawler.naver_politics_news_url}")
    String politicsUrl;

    @Value("${crawler.naver_economy_news_url}")
    String economyUrl;

    @Value("${crawler.naver_society_news_url}")
    String societyUrl;

    @Value("${crawler.naver_life_news_url}")
    String lifeUrl;

    @Value("${crawler.naver_world_news_url}")
    String worldUrl;

    @Value("${crawler.naver_science_news_url}")
    String scienceUrl;

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
//    @Scheduled(cron = "0 0/2 9-15 ? * MON-FRI") // 매 2분마다, 9시부터 17시까지, 월-금 삼성전자 주식 정보 수집
//    public void startFinanceCrawling1() {
//        String url = baseStockPriceUrl + "005930";
//        webCrawlingService.stockPriceCrawling(url, maxDepth);
//    }

    /**
     * 2분 30초(150초)마다 작동하는 스케줄러
     * 월요일부터 금요일, 오전 9시 0분부터 오후 5시 57분 30초까지
     */
//    @Scheduled(cron = "30 0/2 9-15 ? * MON-FRI") // 매 2분마다 30초에 시작, 9시부터 17시까지, 월-금 SK하이닉스 주식 정보 수집
//    public void startFinanceCrawling2() {
//        String url = baseStockPriceUrl + "000660";
//        webCrawlingService.stockPriceCrawling(url, maxDepth);
//    }

    /**
     * 2분 45초(165초)마다 작동하는 스케줄러
     * 월요일부터 금요일, 오전 9시 0분부터 오후 5시 57분 15초까지
     */
//    @Scheduled(cron = "45 0/2 9-15 ? * MON-FRI") // 매 2분마다 45초에 시작, 9시부터 17시까지, 월-금 현대자동차 주식 정보 수집
//    public void startFinanceCrawling3() {
//        String url = baseStockPriceUrl + "005380";
//        webCrawlingService.stockPriceCrawling(url, maxDepth);
//    }

    @Scheduled(fixedDelayString = "#{T(java.util.concurrent.ThreadLocalRandom).current().nextLong(30000, 60000)}") // 5분 ~ 1시간 사이 랜덤 딜레이
    public void startCnnArticleCrawling() {
        String schedulerName = "CNN Article Crawler Scheduler";
        String methodName = "startArticleCrawling";

        LogUtil.logSchedulerStart(schedulerName, methodName); // 스케줄러 시작 로그

        try {
            webCrawlingService.articleCrawling(baseArticleUrl, 0);
            LogUtil.logSchedulerCompletion(schedulerName, methodName, "기사 크롤링 작업 성공적으로 완료."); // 완료 로그
        } catch (Exception e) {
            // 스케줄러 실행 중 최상위 예외 처리
            LogUtil.logSchedulerException(schedulerName, methodName, e, "기사 크롤링 작업 중 예상치 못한 오류 발생."); // 예외 로그
        }
    }

    @Scheduled(fixedDelayString = "#{T(java.util.concurrent.ThreadLocalRandom).current().nextLong(1800000, 3600000)}") // 30분 ~ 1시간 사이 랜덤 딜레이
    public void startNaverNewsArticleCrawling() {
        String schedulerName = "Naver News Article Crawler Scheduler";
        String methodName = "startNaverNewsArticleCrawling";

        List<String> newsUrls = Arrays.asList(
                politicsUrl,
                economyUrl,
                societyUrl,
                lifeUrl,
                worldUrl,
                scienceUrl
        );

        LogUtil.logSchedulerStart(schedulerName, methodName); // 스케줄러 시작 로그

        try {
            for (String url : newsUrls) {
                webCrawlingService.naverNewsCrawling(url, 0);
            }
            LogUtil.logSchedulerCompletion(schedulerName, methodName, "기사 크롤링 작업 성공적으로 완료."); // 완료 로그
        } catch (Exception e) {
            // 스케줄러 실행 중 최상위 예외 처리
            LogUtil.logSchedulerException(schedulerName, methodName, e, "기사 크롤링 작업 중 예상치 못한 오류 발생."); // 예외 로그
        }
    }
}
