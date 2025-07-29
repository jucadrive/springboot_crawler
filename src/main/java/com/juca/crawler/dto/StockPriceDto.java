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
    private Integer changePrice;
    private String changeRatio;
    private Long tradeVolume;
    private Long tradingValue;
    private Integer openingPrice;
    private Integer highPrice;
    private Integer lowPrice;
    private Integer endingPrice;
    private String marketCap;
    private String marketCapRank;
    private Long listedSharesCount;
    private Integer parValue;
    private Integer tradingUnit;
    private String investmentOpinion;
    private Integer targetPrice;
    private Integer fiftyTwoWeekHigh;
    private Integer fiftyTwoWeekLow;
    private String currentPer;
    private Integer currentEps;
    private String pbr;
    private Integer bps;
    private String dividendYield;
    private String industryPer;
    private String industryFluctuationRate;
    private Integer statusCode;
    private String errorMessage;
    private LocalDateTime collectedAt;
}