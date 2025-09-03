package com.juca.crawler.service;

public interface WebCrawlingService {

    void startWebCrawling(String startUrl, int maxDepth);
    void stockPriceCrawling(String startUrl, int maxDepth);
    void articleCrawling(String startUrl, int maxDepth);
    void naverNewsCrawling(String url, int maxDepth);

}
