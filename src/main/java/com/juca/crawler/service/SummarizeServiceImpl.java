package com.juca.crawler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.juca.crawler.dto.ArticleDto;
import com.juca.crawler.dto.SummarizeReqDto;
import com.juca.crawler.dto.SummarizeResDto;
import com.juca.crawler.repository.CrawledNewsArticleRepository;
import com.juca.crawler.util.LogUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Type;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SummarizeServiceImpl implements SummarizeService {

    private final CrawledNewsArticleRepository crawledNewsArticleRepository;

    @Override
    public SummarizeResDto getSummarizedArticleAndKeywords(Long id) {

        SummarizeReqDto reqDto = new SummarizeReqDto();
        SummarizeResDto resDto = new SummarizeResDto();

        try {
            ArticleDto articleDto = crawledNewsArticleRepository.findByArticleId(id);

            reqDto.setText(articleDto.getArticle());

            String apiResponse = WebClient.builder().baseUrl("http://localhost:8000")
                    .build()
                    .post()
                    .uri("/api/v1/summarize")
                    .bodyValue(reqDto)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(apiResponse);

            int resultCode = jsonNode.path("responseCode").asInt();

            if (resultCode == 200) {
                JsonNode summaryNode = jsonNode.path("summary");
                resDto.setSummarizedArticle(summaryNode.asText());

                JsonNode keywordsNode = jsonNode.path("keywords");
                ArrayNode arrayNode = (ArrayNode) keywordsNode;
                List<String> extractedKeywords = objectMapper.convertValue(arrayNode, new TypeReference<>() {
                    @Override
                    public Type getType() {
                        return super.getType();
                    }
                });
                resDto.setExtractedKeywords(extractedKeywords);
            }
        } catch (Exception e) {
            LogUtil.logError("기사 요약 중 에러 발생: " + e.getMessage(), e);
        }
        return resDto;
    }
}
