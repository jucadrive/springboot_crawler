package com.juca.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for {@link com.juca.crawler.domain.StockPrice}
 */
@Getter
@Setter
@NoArgsConstructor
public class StockPriceDto {
    private String stockCode;
    private String stockNm;
    private Integer currentPrice;
    private Integer change;
    private BigDecimal changeRate;
    private Long volume;
    private Long tradingValue;
    private Integer openingPrice;
    private Integer highPrice;
    private Integer lowPrice;
    private Integer endingPrice;
    private Integer statusCode;
    private String errorMessage;
    private LocalDateTime collectedAt;
}