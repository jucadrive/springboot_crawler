package com.juca.crawler.domain;

import com.juca.crawler.dto.StockPriceDto;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "stock_prices")
public class StockPrice extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "stock_nm", nullable = false, length = 50)
    private String stockNm;

    @Column(name = "current_price", nullable = false)
    private Integer currentPrice;

    @Column(name = "change_price", nullable = false)
    private Integer change;

    @Column(name = "change_ratio", nullable = false, precision = 20, scale = 6)
    private BigDecimal changeRate;

    @Column(name = "trade_volume", nullable = false)
    private Long volume;

    @Column(name = "trading_value", nullable = false)
    private Long tradingValue;

    @Column(name = "opening_price", nullable = false)
    private Integer openingPrice;

    @Column(name = "high_price", nullable = false)
    private Integer highPrice;

    @Column(name = "low_price", nullable = false)
    private Integer lowPrice;

    @Column(name = "ending_price")
    private Integer endingPrice;

    @Column(name = "market_cap", length = 50)
    private String marketCap;

    @Column(name = "market_cap_rank", length = 20)
    private String marketCapRank;

    @Column(name = "listed_shares_count")
    private Long listedSharesCount;

    @Column(name = "par_value")
    private Integer parValue;

    @Column(name = "trading_unit")
    private Integer tradingUnit;

    @Column(name = "investment_opinion", length = 10)
    private String investmentOpinion;

    @Column(name = "target_price")
    private Integer targetPrice;

    @Column(name = "52_week_high")
    private Integer fiftyTwoWeekHigh;

    @Column(name = "52_week_low")
    private Integer fiftyTwoWeekLow;

    @Column(name = "current_per", length = 10)
    private String currentPer;

    @Column(name = "current_eps")
    private Integer currentEps;

    @Column(name = "pbr", length = 10)
    private String pbr;

    @Column(name = "bps")
    private Integer bps;

    @Column(name = "dividend_yield", length = 10)
    private String dividendYield;

    @Column(name = "industry_per", length = 10)
    private String industryPer;

    @Column(name = "industry_fluctuation_rate", length = 10)
    private String industryFluctuationRate;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Builder
    public StockPrice(String stockCode, String stockNm, Integer currentPrice, Integer change,
                      BigDecimal changeRate, Long volume, Long tradingValue, Integer openingPrice, Integer highPrice,
                      Integer lowPrice, Integer endingPrice, String marketCap, String marketCapRank, Long listedSharesCount, Integer parValue,
                      Integer tradingUnit, String investmentOpinion, Integer targetPrice, Integer fiftyTwoWeekHigh, Integer fiftyTwoWeekLow,
                      String currentPer, Integer currentEps, String pbr, Integer bps, String dividendYield, String industryPer, String industryFluctuationRate,
                      Integer statusCode, String errorMessage, LocalDateTime collectedAt) {
        this.stockCode = stockCode;
        this.stockNm = stockNm;
        this.currentPrice = currentPrice;
        this.change = change;
        this.changeRate = changeRate;
        this.volume = volume;
        this.tradingValue = tradingValue;
        this.openingPrice = openingPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.endingPrice = endingPrice;
        this.marketCap = marketCap;
        this.marketCapRank = marketCapRank;
        this.listedSharesCount = listedSharesCount;
        this.parValue = parValue;
        this.tradingUnit = tradingUnit;
        this.investmentOpinion = investmentOpinion;
        this.targetPrice = targetPrice;
        this.fiftyTwoWeekHigh = fiftyTwoWeekHigh;
        this.fiftyTwoWeekLow = fiftyTwoWeekLow;
        this.currentPer = currentPer;
        this.currentEps = currentEps;
        this.pbr = pbr;
        this.bps = bps;
        this.dividendYield = dividendYield;
        this.industryPer = industryPer;
        this.industryFluctuationRate = industryFluctuationRate;
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
        this.collectedAt = collectedAt;
    }

    public static StockPrice dtoToEntity(StockPriceDto dto) {
        return StockPrice.builder()
                .stockCode(dto.getStockCode())
                .stockNm(dto.getStockNm())
                .currentPrice(dto.getCurrentPrice())
                .change(dto.getChangePrice())
                .changeRate(dto.getChangeRatio())
                .volume(dto.getTradeVolume())
                .tradingValue(dto.getTradingValue())
                .openingPrice(dto.getOpeningPrice())
                .highPrice(dto.getHighPrice())
                .lowPrice(dto.getLowPrice())
                .endingPrice(dto.getEndingPrice())
                .marketCap(dto.getMarketCap())
                .marketCapRank(dto.getMarketCap())
                .listedSharesCount(dto.getListedSharesCount())
                .parValue(dto.getParValue())
                .tradingUnit(dto.getTradingUnit())
                .investmentOpinion(dto.getInvestmentOpinion())
                .targetPrice(dto.getTargetPrice())
                .fiftyTwoWeekHigh(dto.getFiftyTwoWeekHigh())
                .fiftyTwoWeekLow(dto.getFiftyTwoWeekLow())
                .currentPer(dto.getCurrentPer())
                .currentEps(dto.getCurrentEps())
                .pbr(dto.getPbr())
                .bps(dto.getBps())
                .dividendYield(dto.getDividendYield())
                .industryPer(dto.getIndustryPer())
                .industryFluctuationRate(dto.getIndustryFluctuationRate())
                .statusCode(dto.getStatusCode())
                .errorMessage(dto.getErrorMessage())
                .collectedAt(LocalDateTime.now())
                .build();
    }
}