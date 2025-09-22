package com.juca.crawler.service;

import com.juca.crawler.dto.SummarizeReqDto;
import com.juca.crawler.dto.SummarizeResDto;

public interface SummarizeService {

    SummarizeResDto getSummarizedArticleAndKeywords(Long id);
}
