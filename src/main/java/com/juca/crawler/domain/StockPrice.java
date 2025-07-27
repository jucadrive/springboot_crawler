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

    @Column(name = "change", nullable = false)
    private Integer change;

    @Column(name = "change_rate", nullable = false, precision = 20, scale = 6)
    private BigDecimal changeRate;

    @Column(name = "volume", nullable = false)
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

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Builder
    public StockPrice(String stockCode, String stockNm, Integer currentPrice, Integer change,
                      BigDecimal changeRate, Long volume, Long tradingValue, Integer openingPrice, Integer highPrice,
                      Integer lowPrice, Integer endingPrice, Integer statusCode, String errorMessage, LocalDateTime collectedAt) {
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
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
        this.collectedAt = collectedAt;
    }

    public static StockPrice dtoToEntity(StockPriceDto dto) {
        return StockPrice.builder()
                .stockCode(dto.getStockCode())
                .stockNm(dto.getStockNm())
                .currentPrice(dto.getCurrentPrice())
                .change(dto.getChange())
                .changeRate(dto.getChangeRate())
                .volume(dto.getVolume())
                .tradingValue(dto.getTradingValue())
                .openingPrice(dto.getOpeningPrice())
                .highPrice(dto.getHighPrice())
                .lowPrice(dto.getLowPrice())
                .endingPrice(dto.getEndingPrice())
                .statusCode(dto.getStatusCode())
                .errorMessage(dto.getErrorMessage())
                .collectedAt(LocalDateTime.now())
                .build();
    }
}