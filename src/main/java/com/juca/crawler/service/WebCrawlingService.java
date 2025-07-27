package com.juca.crawler.service;

public interface WebCrawlingService {

    void startWebCrawling(String startUrl, int maxDepth);
    void stockPriceCrawling(String startUrl, int maxDepth);

}
