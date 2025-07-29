package com.juca.crawler.domain;

import com.juca.crawler.dto.StockPriceDto;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "change_price", nullable = false, length = 10)
    private String changePrice;

    @Column(name = "change_rate", nullable = false, length = 10)
    private String changeRate;

    @Column(name = "sales_revenue", nullable = false)
    private Integer salesRevenue;

    @Column(name = "oper_profit", nullable = false)
    private Integer operProfit;

    @Column(name = "adjusted_oper_profit", nullable = false)
    private Integer adjustedOperProfit;

    @Column(name = "oper_profit_growth_rate", nullable = false, length = 10)
    private String operProfitGrowthRate;

    @Column(name = "net_income", nullable = false)
    private Integer netIncome;

    @Column(name = "earning_per_share", nullable = false, length = 20)
    private String earningPerShare;

    @Column(name = "roe", nullable = false, length = 20)
    private String roe;

    @Column(name = "opening_price")
    private Integer openingPrice;

    @Column(name = "high_price")
    private Integer highPrice;

    @Column(name = "low_price")
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

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Builder
    public StockPrice(String stockCode, String stockNm, Integer currentPrice, String changePrice,
                      String changeRate, Integer salesRevenue, Integer operProfit, Integer adjustedOperProfit, String operProfitGrowthRate,
                      Integer netIncome, String earningPerShare, String roe, Integer openingPrice, Integer highPrice,
                      Integer lowPrice, Integer endingPrice, String marketCap, String marketCapRank, Long listedSharesCount, Integer parValue,
                      Integer tradingUnit, String investmentOpinion, Integer targetPrice, Integer fiftyTwoWeekHigh, Integer fiftyTwoWeekLow,
                      String currentPer, Integer currentEps, String pbr, Integer bps, String dividendYield,
                      Integer statusCode, String errorMessage, LocalDateTime collectedAt) {
        this.stockCode = stockCode;
        this.stockNm = stockNm;
        this.currentPrice = currentPrice;
        this.changePrice = changePrice;
        this.changeRate = changeRate;
        this.salesRevenue = salesRevenue;
        this.operProfit = operProfit;
        this.adjustedOperProfit = adjustedOperProfit;
        this.operProfitGrowthRate = operProfitGrowthRate;
        this.netIncome = netIncome;
        this.earningPerShare = earningPerShare;
        this.roe = roe;
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
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
        this.collectedAt = collectedAt;
    }

    public static StockPrice dtoToEntity(StockPriceDto dto) {
        return StockPrice.builder()
                .stockCode(dto.getStockCode())
                .stockNm(dto.getStockNm())
                .currentPrice(dto.getCurrentPrice())
                .changePrice(dto.getChangePrice())
                .changeRate(dto.getChangeRate())
                .salesRevenue(dto.getSalesRevenue())
                .operProfit(dto.getOperProfit())
                .adjustedOperProfit(dto.getAdjustedOperProfit())
                .operProfitGrowthRate(dto.getOperProfitGrowthRate())
                .netIncome(dto.getNetIncome())
                .earningPerShare(dto.getEarningPerShare())
                .roe(dto.getRoe())
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
                .statusCode(dto.getStatusCode())
                .errorMessage(dto.getErrorMessage())
                .collectedAt(LocalDateTime.now())
                .build();
    }
}