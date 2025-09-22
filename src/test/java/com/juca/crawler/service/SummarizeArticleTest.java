package com.juca.crawler.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SummarizeArticleTest {

    @Autowired
    SummarizeService summarizeService;

    @Test
    public void summarizeTest() {

        summarizeService.getSummarizedArticleAndKeywords(111L);
    }
}
