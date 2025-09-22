package com.juca.crawler.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SummarizeResDto {

    private String summarizedArticle;
    private List<String> extractedKeywords;
}
