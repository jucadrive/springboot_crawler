package com.juca.crawler.service;

import com.juca.crawler.domain.CrawledPage;
import com.juca.crawler.domain.ExtractedLink;
import com.juca.crawler.dto.CrawledPageDto;
import com.juca.crawler.dto.ExtractedLinkDto;
import com.juca.crawler.repository.CrawledPageRepository;
import com.juca.crawler.repository.ExtractedLinkRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WebCrawlingServiceImpl implements WebCrawlingService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36";
    private static final int TIME_OUT = 10000;
    private static final String REFERRER = "https://www.naver.com";

    private final CrawledPageRepository crawledPageRepository;
    private final ExtractedLinkRepository extractedLinkRepository;

    // 크롤링 작업 단위를 위한 내부 클래스
    @Getter
    @AllArgsConstructor
    private static final class CrawlTask {
        String url;
        int depth;
        Long parentPageId;
    }

    @Override
    public void startWebCrawling(String startUrl, int maxDepth) {
        Queue<CrawlTask> crawlQueue = new LinkedList<>();
        Set<String> visitedUrls = new HashSet<>();

        crawlQueue.add(new CrawlTask(startUrl, 0, null));
        visitedUrls.add(startUrl);

        while (!crawlQueue.isEmpty()) {
            CrawlTask currentTask = crawlQueue.poll();
            String currentUrl = currentTask.getUrl();
            int currentDepth = currentTask.getDepth();
            Long currentParentId = currentTask.getParentPageId();

            // 최대 깊이 도달 체크
            if (currentDepth > maxDepth) {
                System.out.println("최대 크롤링 깊이 도달: " + currentUrl);
                continue;   // 다음 큐 아이템으로 넘어감
            }

            // DB에 이미 존재하는지 확인
            if (crawledPageRepository.findByUrl(currentUrl).isPresent()) {
                System.out.println("이미 크롤링한 페이지: " + currentUrl);
                continue;
            }

            System.out.println("크롤링 시작: " + currentUrl + " (깊이: " + currentDepth + ")");

            CrawledPageDto crawledPageDto = new CrawledPageDto();
            crawledPageDto.setUrl(currentUrl);
            crawledPageDto.setCrawlDepth(currentDepth);
            crawledPageDto.setParentPageId(currentParentId);
            crawledPageDto.setCrawledAt(LocalDateTime.now());

            String domain = getDomainFromUrl(currentUrl);
            crawledPageDto.setDomain(domain);

            Document doc = null;
            Long newPageId = null;

            try {
                // Jsoup Connection 및 Response 획득
                Connection.Response response = Jsoup.connect(currentUrl)
                        .userAgent(USER_AGENT)
                        .referrer(REFERRER)
                        .timeout(TIME_OUT)
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .execute();

                int statusCode = response.statusCode();
                String contentType = response.contentType();
                String htmlContent = null;
                String pageTitle = null;
                String metaDescription = null;

                crawledPageDto.setStatusCode(statusCode);
                crawledPageDto.setContentType(contentType);

                // Content-Type이 text/html이고 statusCode = 200일 경우에만
                if (contentType != null && contentType.startsWith("text/html") && statusCode == 200) {
                    htmlContent = response.body();
                    doc = Jsoup.parse(htmlContent, currentUrl); // base uri 설정(상대경로 링크 처리)
                    pageTitle = doc.title();

                    // og:description, description 메타 태그 모두 확인
                    Element descriptionMeta = doc.select("meta[property=og:description]").first();
                    if (descriptionMeta != null) metaDescription = descriptionMeta.attr("content");

                    crawledPageDto.setHtmlContent(htmlContent);
                    crawledPageDto.setTitle(pageTitle);
                    crawledPageDto.setMetaDescription(metaDescription);
                } else {
                    String errorMessage = "Non-HTML content or non-200 status: " + statusCode + ", Type: " + contentType;
                    crawledPageDto.setErrorMessage(errorMessage);
                    System.err.println("  " + errorMessage + " for URL: " + currentUrl);
                }

                // 부모 페이지 엔티티 조회 (있을 경우)
                CrawledPage parentPage = null;

                if (currentParentId != null) {
                    parentPage = crawledPageRepository.findById(currentParentId).orElse(null);
                }

                // DTO를 Entity로 변환하여 저장
                CrawledPage crawledPage = CrawledPage.dtoToEntity(crawledPageDto, parentPage);
                crawledPageRepository.save(crawledPage);

                // 저장된 페이지의 ID를 획득하여 자식 링크의 부모 ID로 사용
                newPageId = crawledPage.getId();

                // HTML 파싱이 성공했고, 새로운 페이지 ID가 부여된 경우에만 링크 추출 및 저장
                if (doc != null && newPageId != null) {
                    Elements links = doc.select("a[href]");
                    Set<String> extractedLinksOnCurrentPage = new HashSet<>(); // ★★★ 현재 페이지에서 추출된 링크 URL들을 담을 Set ★★★

                    for (Element link : links) {
                        String absUrl = link.attr("abs:href");
                        String linkText = link.text().trim();

                        // 유효하지 않은 링크 스킵
                        if (absUrl.isEmpty() || absUrl.startsWith("#") || absUrl.startsWith("mailto:") || absUrl.startsWith("tel:") || absUrl.startsWith("javascript:")) {
                            continue;
                        }

                        // ★★★ 현재 페이지에서 이미 추출된 동일 URL이라면 스킵 ★★★
                        if (!extractedLinksOnCurrentPage.add(absUrl)) {
                            System.out.println("  [중복 링크 스킵] 현재 페이지에서 이미 추출된 링크: " + absUrl);
                            continue;
                        }

                        // ExtractedLinkDto 생성 및 엔티티 저장
                        ExtractedLinkDto extractedLinkDto = new ExtractedLinkDto();
                        extractedLinkDto.setSourcePageId(newPageId);    // 부모 페이지 ID
                        extractedLinkDto.setLinkUrl(absUrl);
                        extractedLinkDto.setLinkText(linkText);
                        extractedLinkDto.setLinkType(determineLinkType(absUrl, domain));
                        extractedLinkDto.setCrawledAt(LocalDateTime.now());

                        // ExtractedLink 엔티티로 변환 및 저장
                        ExtractedLink extractedLink = ExtractedLink.dtoToEntity(extractedLinkDto, crawledPage);
                        extractedLinkRepository.save(extractedLink);

                        // 동일 도메인 내의 링크만 큐에 추가(최대 깊이 초과하지 않고, 이미 방문했거나 DB에 없는 경우)
                        String linkDomain = getDomainFromUrl(absUrl);
                        if (domain != null && domain.equals(linkDomain) && !visitedUrls.contains(absUrl)) {
                            if (crawledPageRepository.findByUrl(absUrl).isEmpty()) {
                                visitedUrls.add(absUrl);
                                crawlQueue.add(new CrawlTask(absUrl, currentDepth + 1, newPageId));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 예외 발생 시 crawledPageDto에 에러 메시지 설정
                crawledPageDto.setErrorMessage(e.getMessage());
                System.err.println("크롤링 중 예외 발생: " + currentUrl + " - " + e.getMessage());
            } finally {
                // ★★★ 이 부분이 중요! 예외 발생 여부와 관계없이 딜레이를 적용합니다. ★★★
                try {
                    long delay = 1000 + (long) (Math.random() * 3000); // 1초 ~ 4초 랜덤 딜레이
                    System.out.println("딜레이 중: " + delay + "ms");
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                    System.err.println("딜레이 중 인터럽트 발생: " + ie.getMessage());
                }
            }
        }
        System.out.println("크롤링 완료!");
    }

    @Override
    public void stockPriceCrawling(String startUrl, int maxDepth) {
        Queue<CrawlTask> crawlQueue = new LinkedList<>();
        Set<String> visitedUrls = new HashSet<>();

        crawlQueue.add(new CrawlTask(startUrl, 0, null));
        visitedUrls.add(startUrl);

        while (!crawlQueue.isEmpty()) {
            CrawlTask currentTask = crawlQueue.poll();
            String currentUrl = currentTask.getUrl();
            int currentDepth = currentTask.getDepth();
            Long currentParentId = currentTask.getParentPageId();

            // 최대 깊이 도달 체크
            if (currentDepth > maxDepth) {
                continue;
            }

            System.out.println("크롤링 시작: " + currentUrl + " (깊이: " + currentDepth + ")");

            Document doc = null;

            try {
                Connection.Response response = Jsoup.connect(currentUrl)
                        .userAgent(USER_AGENT)
                        .referrer(REFERRER)
                        .timeout(TIME_OUT)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .execute();

                int statusCode = response.statusCode();
                String contentType = response.contentType();
                String htmlContent = null;

                System.out.println("상태 코드: " + statusCode);

                if (contentType != null && contentType.startsWith("text/html") && statusCode == 200) {
                    htmlContent = response.body();
                    doc = Jsoup.parse(htmlContent);
                    // 1. 종목명 (삼성전자)
                    String stockName = null;
                    // dl.blind 안의 dt 태그 안의 strong 태그
                    Element stockNameElement = doc.select(".rate_info dl.blind dt strong").first();
                    if (stockNameElement != null) {
                        stockName = stockNameElement.text().trim();
                    }
                    System.out.println("종목명: " + stockName);

                    // 2. 종목코드 (이 HTML에는 직접적인 종목코드 없음 - URL에서 추출해야 함)
                    // 이 HTML 조각에는 '000660' 같은 종목코드가 보이지 않습니다.
                    // 종목코드는 이전처럼 URL (https://finance.daum.net/quotes/A000660#home)에서 추출해야 합니다.
                    String stockCode = null;
                    // 클래스가 'description'인 div 태그 내부  클래스가 'code'인 span 태그
                    Element stockCodeElement = doc.select("div.description > span.code").first();
                    if (stockCodeElement != null) {
                        stockCode = stockCodeElement.text().trim();
                    }
                    System.out.println("종목코드: " + stockCode);


                    // 3. 현재가 (70,300)
                    String currentPrice = null;
                    // .today p.no_today em.no_up 안에 있는 span.blind
                    Element currentPriceElement = doc.select(".today p.no_today em.no_up span.blind").first();
                    if (currentPriceElement != null) {
                        currentPrice = currentPriceElement.text().trim();
                    }
                    System.out.println("현재가: " + currentPrice);


                    // 4. 전일 대비 (4,400)
                    String changePrice = null;
                    // .today p.no_exday 안의 첫 번째 em.no_up 안에 있는 span.blind
                    Element changePriceElement = doc.select(".today p.no_exday em.no_up span.blind").first();
                    if (changePriceElement != null) {
                        changePrice = changePriceElement.text().trim();
                    }
                    System.out.println("전일 대비: " + changePrice);


                    // 5. 등락률 (6.68%)
                    String changeRatio = null;
                    // .today p.no_exday 안의 두 번째 em.no_up 안에 있는 span.blind
                    Element changeRatioBlindElement = doc.select(".today p.no_exday em.no_up:nth-of-type(2) span.blind").first();
                    if (changeRatioBlindElement != null) {
                        changeRatio = changeRatioBlindElement.text().trim() + "%"; // %는 별도 span으로 되어 있으니 붙여줍니다.
                    }
                    System.out.println("등락률: " + changeRatio);


                    // 6. 전일 종가 (65,900)
                    String prevClosingPrice = null;
                    // table.no_info tbody tr:nth-of-type(1) td.first em span.blind
                    Element prevClosingPriceElement = doc.select("table.no_info tbody tr:nth-of-type(1) td.first em span.blind").first();
                    if (prevClosingPriceElement != null) {
                        prevClosingPrice = prevClosingPriceElement.text().trim();
                    }
                    System.out.println("전일 종가: " + prevClosingPrice);


                    // 7. 고가 (70,400)
                    String highPrice = null;
                    // table.no_info tbody tr:nth-of-type(1) td:nth-of-type(2) em.no_up span.blind
                    Element highPriceElement = doc.select("table.no_info tbody tr:nth-of-type(1) td:nth-of-type(2) em.no_up span.blind").first();
                    if (highPriceElement != null) {
                        highPrice = highPriceElement.text().trim();
                    }
                    System.out.println("고가: " + highPrice);


                    // 8. 거래량 (23,131,575)
                    String tradeVolume = null;
                    // table.no_info tbody tr:nth-of-type(1) td:nth-of-type(3) em span.blind
                    Element tradeVolumeElement = doc.select("table.no_info tbody tr:nth-of-type(1) td:nth-of-type(3) em span.blind").first();
                    if (tradeVolumeElement != null) {
                        tradeVolume = tradeVolumeElement.text().trim();
                    }
                    System.out.println("거래량: " + tradeVolume);


                    // 9. 시가 (68,200)
                    String openingPrice = null;
                    // em 태그 안에서 클래스 이름이 'no'로 시작하거나 'shim'인 span 태그들만 선택
                    Elements openingPriceSpans = doc.select("div#rate_info_krx table.no_info tbody tr:nth-of-type(2) td.first em span[class^=no], div#rate_info_krx table.no_info tbody tr:nth-of-type(2) td.first em span.shim");
                    if (!openingPriceSpans.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Element span : openingPriceSpans) {
                            sb.append(span.text());
                        }
                        openingPrice = sb.toString();
                    }
                    System.out.println("시가: " + openingPrice);


                    // 10. 저가 (65,600)
                    String lowPrice = null;
                    // em 태그 안에서 클래스 이름이 'no'로 시작하거나 'shim'인 span 태그들만 선택
                    Elements lowPriceSpans = doc.select("div#rate_info_krx table.no_info tbody tr:nth-of-type(2) td:nth-of-type(2) em.no_up span[class^=no], div#rate_info_krx table.no_info tbody tr:nth-of-type(2) td:nth-of-type(2) em.no_up span.shim");
                    if (!lowPriceSpans.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Element span : lowPriceSpans) {
                            sb.append(span.text());
                        }
                        lowPrice = sb.toString();
                    }
                    System.out.println("저가: " + lowPrice);


                    // 11. 거래대금 (1,590,077 백만)
                    String tradePrice = null;
                    // table.no_info tbody tr:nth-of-type(2) td:nth-of-type(3) em span.blind
                    Element tradePriceElement = doc.select("table.no_info tbody tr:nth-of-type(2) td:nth-of-type(3) em span.blind").first();
                    if (tradePriceElement != null) {
                        // '백만' 텍스트는 별도의 span에 있으므로 blind 값만 가져옵니다.
                        // 그리고 필요하다면 뒤에 '백만'을 붙여줍니다.
                        tradePrice = tradePriceElement.text().trim();
                        // String tradePriceUnit = doc.select("table.no_info tbody tr:nth-of-type(2) td:nth-of-type(3) span.sptxt.sp_txt11").first().text().trim();
                        // tradePrice = tradePrice + tradePriceUnit;
                    }
                    System.out.println("거래대금: " + tradePrice + " 백만"); // '백만'은 파싱 후 붙여주거나, parseTradeValue 함수에서 처리

                    // 모든 파싱은 'tab_con1' div 안에서 이루어지도록 셀렉터 시작 부분을 한정합니다.
                    Element tabCon1 = doc.selectFirst("div#tab_con1");

                    if (tabCon1 == null) {
                        System.out.println("투자정보 탭 (div#tab_con1)을 찾을 수 없습니다.");
                        return;
                    }

                    // 1. 시가총액 정보
                    String marketCap = "";
                    Element marketCapEm = tabCon1.selectFirst("table[summary='시가총액 정보'] em#_market_sum");
                    if (marketCapEm != null) {
                        // "416조 7,425"를 얻고, 공백을 제거하고 '조'를 '조 '로 유지하여 파싱합니다.
                        // .text()로 가져온 후 불필요한 공백과 줄바꿈을 제거합니다.
                        marketCap = marketCapEm.text().replaceAll("\\s+", " ").trim();
                        // "조"와 숫자 사이에 공백이 남아있는 경우를 대비하여 추가 처리할 수 있습니다.
                        marketCap = marketCap.replace("조 ", "조"); // 예: "416조 7,425" -> "416조7,425" (필요시)
                        marketCap = marketCap + "억원"; // "억원"은 em 태그 밖에 있으므로 수동으로 추가
                    }
                    System.out.println("시가총액: " + marketCap);

                    // 2. 시가총액 순위
                    String marketCapRank = "";
                    Element marketCapRankTd = tabCon1.selectFirst("table[summary='시가총액 정보'] th:has(a[href*='sise_market_sum.naver']) + td");
                    if (marketCapRankTd != null) {
                        marketCapRank = marketCapRankTd.text().trim();
                    }
                    System.out.println("시가총액 순위: " + marketCapRank);

                    // 3. 상장주식수
                    String listedSharesCount = "";
                    Element listedSharesEm = tabCon1.selectFirst("table[summary='시가총액 정보'] th:contains(상장주식수) + td em");
                    if (listedSharesEm != null) {
                        listedSharesCount = listedSharesEm.text().trim();
                    }
                    System.out.println("상장주식수: " + listedSharesCount);

                    // 4. 액면가 및 매매단위
                    String parValue = "";
                    String tradingUnit = "";
                    Element parValueTradingUnitTd = tabCon1.selectFirst("table[summary='시가총액 정보'] th:contains(액면가) + td");
                    if (parValueTradingUnitTd != null) {
                        // 액면가
                        Element parValueEm = parValueTradingUnitTd.selectFirst("em:nth-of-type(1)");
                        if (parValueEm != null) {
                            parValue = parValueEm.text().trim() + "원";
                        }
                        // 매매단위
                        Element tradingUnitEm = parValueTradingUnitTd.selectFirst("em:nth-of-type(2)");
                        if (tradingUnitEm != null) {
                            tradingUnit = tradingUnitEm.text().trim() + "주";
                        }
                    }
                    System.out.println("액면가: " + parValue);
                    System.out.println("매매단위: " + tradingUnit);

                    // --- 외국인 관련 정보는 div.gray로 마크되어 있어 이 섹션은 건너뜁니다. ---

                    // 5. 투자의견 및 목표주가
                    String investmentOpinion = "";
                    String targetPrice = "";
                    Element investmentOpinionTd = tabCon1.selectFirst("table[summary='투자의견 정보'] th:contains(투자의견) + td");
                    if (investmentOpinionTd != null) {
                        // 투자의견 (예: 4.00매수)
                        // 'f_up' 클래스를 가진 span 바로 아래에 있는 em을 선택
                        Element opinionEm = investmentOpinionTd.selectFirst("span.f_up em");
                        if (opinionEm != null) {
                            // opinionEm의 부모인 span.f_up의 텍스트 전체를 가져와야 "4.00매수"가 나옵니다.
                            investmentOpinion = opinionEm.parent().text().trim();
                        }

                        // 목표주가 (<td> 내의 두 번째 <em> 태그를 선택)
                        Element targetPriceEm = investmentOpinionTd.select("em").get(1); // 두 번째 em 태그
                        targetPrice = targetPriceEm.text().trim();
                    }
                    System.out.println("투자의견: " + investmentOpinion);
                    System.out.println("목표주가: " + targetPrice);

                    // 6. 52주 최고/최저가
                    String fiftyTwoWeekHigh = "";
                    String fiftyTwoWeekLow = "";
                    Element fiftyTwoWeekTd = tabCon1.selectFirst("table[summary='투자의견 정보'] th:contains(52주최고) + td");
                    if (fiftyTwoWeekTd != null) {
                        Elements priceEms = fiftyTwoWeekTd.select("em");
                        if (priceEms.size() >= 1) {
                            fiftyTwoWeekHigh = priceEms.get(0).text().trim();
                        }
                        if (priceEms.size() >= 2) {
                            fiftyTwoWeekLow = priceEms.get(1).text().trim();
                        }
                    }
                    System.out.println("52주 최고가: " + fiftyTwoWeekHigh);
                    System.out.println("52주 최저가: " + fiftyTwoWeekLow);

                    // 7. PER / EPS (현재)
                    String currentPER = "";
                    String currentEPS = "";
                    Element perEpsTd = tabCon1.selectFirst("table[summary='PER/EPS 정보'] th:contains(PER) + td");
                    if (perEpsTd != null) {
                        currentPER = perEpsTd.selectFirst("em#_per").text().trim();
                        currentEPS = perEpsTd.selectFirst("em#_eps").text().trim();
                    }
                    System.out.println("현재 PER: " + currentPER);
                    System.out.println("현재 EPS: " + currentEPS);

                    // 10. PBR / BPS
                    String pbr = "";
                    String bps = "";
                    Element pbrBpsTd = tabCon1.selectFirst("table[summary='PER/EPS 정보'] th:contains(PBR) + td");
                    if (pbrBpsTd != null) {
                        pbr = pbrBpsTd.selectFirst("em#_pbr").text().trim();
                        // PBR 옆의 BPS는 ID가 없으므로 두 번째 em 태그를 선택
                        Elements pbrBpsEms = pbrBpsTd.select("em");
                        if (pbrBpsEms.size() >= 2) {
                            bps = pbrBpsEms.get(1).text().trim();
                        }
                    }
                    System.out.println("PBR: " + pbr);
                    System.out.println("BPS: " + bps);

                    // 11. 배당수익률
                    String dividendYield = "";
                    Element dividendYieldTd = tabCon1.selectFirst("table[summary='PER/EPS 정보'] th:contains(배당수익률) + td");
                    if (dividendYieldTd != null) {
                        Element dividendYieldEm = dividendYieldTd.selectFirst("em#_dvr");
                        if (dividendYieldEm != null) {
                            dividendYield = dividendYieldEm.text().trim();
                        }
                    }
                    System.out.println("배당수익률: " + dividendYield);

                    // 12. 동일업종 PER
                    String industryPER = "";
                    Element industryPerTd = tabCon1.selectFirst("table[summary='동일업종 PER 정보'] th:contains(동일업종 PER) + td em");
                    if (industryPerTd != null) {
                        industryPER = industryPerTd.text().trim();
                    }
                    System.out.println("동일업종 PER: " + industryPER);

                    // 13. 동일업종 등락률
                    String industryFluctuationRate = "";
                    Element industryFluctuationTd = tabCon1.selectFirst("table[summary='동일업종 PER 정보'] th:contains(동일업종 등락률) + td em");
                    if (industryFluctuationTd != null) {
                        industryFluctuationRate = industryFluctuationTd.text().trim();
                    }
                    System.out.println("동일업종 등락률: " + industryFluctuationRate);
                }
            } catch (Exception e) {
                System.err.println("크롤링 중 에러 발생: " + currentUrl + " - " + e.getMessage());
            }
        }
    }

    private String getDomainFromUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            if (host.startsWith("www.")) {
                return host.substring(4);
            }
            return host;
        } catch (MalformedURLException e) {
            return null; // 유효하지 않은 URL
        }
    }

    // 링크 타입 분류 함수 (선택 사항)
    private String determineLinkType(String url, String baseDomain) {
        if (url == null || url.isEmpty()) return "unknown";
        if (url.contains(baseDomain)) return "internal";
        if (url.matches(".*\\.(jpg|jpeg|png|gif|bmp|svg)$")) return "image";
        if (url.matches(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx)$")) return "document";
        return "external";
    }
}
