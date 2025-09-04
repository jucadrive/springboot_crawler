package com.juca.crawler.service;

import com.juca.crawler.domain.*;
import com.juca.crawler.dto.*;
import com.juca.crawler.repository.*;
import com.juca.crawler.util.LogUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
public class WebCrawlingServiceImpl implements WebCrawlingService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36";
    private static final int TIME_OUT = 10000;
    private static final String REFERRER = "https://www.naver.com";

    private static final String CNN_REFERRER = "https://edition.cnn.com/";
    private static final Random random = new Random();  // 랜덤 딜레이
    private static final long MIN_DELAY_MS = 1000; // 최소 1초
    private static final long MAX_DELAY_MS = 5000; // 최대 5초

    private final CrawledPageRepository crawledPageRepository;
    private final ExtractedLinkRepository extractedLinkRepository;
    private final StockPriceRepository stockPriceRepository;
    private final CrawledNewsArticleRepository crawledNewsArticleRepository;
    private final CnnArticleRepository cnnArticleRepository;

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
                continue;   // 다음 큐 아이템으로 넘어감
            }

            // DB에 이미 존재하는지 확인
            if (crawledPageRepository.findByUrl(currentUrl).isPresent()) {
                continue;
            }

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
            } finally {
                // ★★★ 이 부분이 중요! 예외 발생 여부와 관계없이 딜레이를 적용합니다. ★★★
                try {
                    long delay = 1000 + (long) (Math.random() * 3000); // 1초 ~ 4초 랜덤 딜레이
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                }
            }
        }
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

                if (contentType != null && contentType.startsWith("text/html") && statusCode == 200) {
                    htmlContent = response.body();
                    doc = Jsoup.parse(htmlContent);

                    // 테이블 데이터를 저장할 리스트
                    Map<String, String> stockDataMap = new LinkedHashMap<>();

//                  ==============================================================================

                    // ----------------------------------------------------
                    // 1. 동종업종 비교 테이블에서 첫 번째 종목 정보 파싱
                    // ----------------------------------------------------
                    parseComparativeTable(doc, stockDataMap);

                    // ----------------------------------------------------
                    // 2. tab_con1 영역에서 추가 정보 파싱
                    // ----------------------------------------------------
                    parseTabCon1Section(doc, stockDataMap);

                    StockPriceDto stockDto = mapToStockPriceDto(stockDataMap);
                    StockPrice entity = StockPrice.dtoToEntity(stockDto);
                    stockPriceRepository.save(entity);
                }
            } catch (Exception e) {
                LogUtil.logError("주식 크롤링 중 에러 발생: " + startUrl + " - " + e.getMessage(), e);
            }
        }
    }

    /**
     *
     * @param url 네이버 뉴스 URL
     * @param maxDepth 크롤링 깊이
     */
    @Override
    public void naverNewsCrawling(String url, int maxDepth) {
        Set<String> visitedArticle = new HashSet<>();
        String htmlContent;
        Document doc;
        int statusCode;
        String contentType;

        try {
            Connection.Response response = Jsoup.connect(url).userAgent(USER_AGENT)
                    .referrer(REFERRER)
                    .timeout(TIME_OUT)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .execute();

            statusCode = response.statusCode();
            contentType = response.contentType();

            // Content-Type이 text/html이고 statusCode = 200일 경우에만
            if (contentType != null && contentType.startsWith("text/html") && statusCode == 200) {
                htmlContent = response.body();
                doc = Jsoup.parse(htmlContent);

                Elements articleLinks = doc.select("a._NLOG_IMPRESSION");

                for (Element link : articleLinks) {
                    String articleUrl = link.attr("href");

                    if (crawledNewsArticleRepository.findByArticleUrl(articleUrl).isPresent()) {
                        continue;
                    }

                    if (visitedArticle.contains(articleUrl)) {
                        continue;
                    }

                    if (articleUrl.startsWith("http")) {
                        crawlArticle(articleUrl);
                        visitedArticle.add(articleUrl);
                    }
                }
            }
        } catch (IOException e) {
            LogUtil.logError("뉴스 크롤링 중 에러 발생: " + url + " - " + e.getMessage(), e);
        } catch (Exception e) {
            LogUtil.logError("알 수 없는 오류 발생: " + url + " - " + e.getMessage(), e);
        }

    }

    private void crawlArticle(String articleUrl) throws IOException {

        CrawledNewsArticleDto dto = new CrawledNewsArticleDto();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try {
            long delay = 30000 + (long) (Math.random() * 30000); // 30초 ~ 1분10초 랜덤 딜레이
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
        }

        String htmlContent;
        Document articleDoc;

        try {
            Connection.Response response = Jsoup.connect(articleUrl).userAgent(USER_AGENT)
                    .referrer("https://news.naver.com/")
                    .timeout(TIME_OUT)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .execute();

            htmlContent = response.body();
            articleDoc = Jsoup.parse(htmlContent);

            String media = null;
            String title = null;
            String article = null;
            String dateStamp = null;
            String author = null;
            String category = null;

            Element mediaElement = articleDoc.select("a.media_end_head_top_logo img").first();
            Element titleElement = articleDoc.select("h2#title_area span").first();
            Element articleContent = articleDoc.select("div#newsct_article").first();
            Element dateElement = articleDoc.select("span.media_end_head_info_datestamp_time").first();
            Element authorElement = articleDoc.select("em.media_end_head_journalist_name").first();
            Element categoryElement = articleDoc.select("li.Nlist_item._LNB_ITEM.is_active").first();

            if (mediaElement != null) {
                media = mediaElement.attr("title");
            }

            if (titleElement != null) {
                title = titleElement.text();
            }

            if (articleContent != null) {
                article = articleContent.text();
            }

            if (dateElement != null) {
                dateStamp = dateElement.attr("data-date-time");
            }

            if (authorElement != null) {
                author = authorElement.text();
            }

            if (categoryElement != null) {
                category = categoryElement.text();
            }

            dto.setArticleUrl(articleUrl);
            dto.setMedia(media);
            dto.setCategory(category);
            dto.setTitle(title);
            dto.setArticle(article);
            dto.setHtmlContent(htmlContent);
            dto.setAuthor(author);
            if (dateStamp != null) dto.setPublishedAt(LocalDateTime.parse(dateStamp, formatter));
            dto.setCrawledAt(LocalDateTime.now());

            crawledNewsArticleRepository.save(CrawledNewsArticle.dtoToEntity(dto));
        } catch (Exception e) {
            LogUtil.logError("뉴스 기사 수집 중 에러 발생: " + articleUrl + " - " + e.getMessage(), e);
        }
    }

    /**
     * 동종업종 비교 테이블에서 첫 번째 종목 (검색한 종목)의 정보를 파싱합니다.
     * @param doc Jsoup Document 객체
     * @param stockDataMap 데이터를 저장할 Map
     */
    private static void parseComparativeTable(Document doc, Map<String, String> stockDataMap) {
        Element table = doc.selectFirst("table.tb_type1.tb_num[summary*='동종업종 비교']");

        if (table == null) {
            return;
        }

        // 종목명과 코드 추출 (첫 번째 종목)
        Element firstStockHeader = table.selectFirst("thead tr th[scope='col']");
        if (firstStockHeader != null) {
            Element link = firstStockHeader.selectFirst("a");
            if (link != null) {
                stockDataMap.put("stockNm", link.ownText().trim()); // 종목명 -> stockNm
                Element codeEm = link.selectFirst("em");
                if (codeEm != null) {
                    stockDataMap.put("stockCode", codeEm.text().trim()); // 종목코드 -> stockCode
                }
            }
        }

        // 바디 데이터 추출 (첫 번째 종목의 데이터만)
        Elements rows = table.select("tbody tr");

        for (Element row : rows) {
            Element itemHeader = row.selectFirst("th[scope='row']");
            if (itemHeader == null) continue;

            String itemName = itemHeader.selectFirst("span").text().trim();

            // 첫 번째 <td>만 선택
            Element firstCell = row.selectFirst("td");
            if (firstCell == null) continue;

            String value;

            // DTO 필드명에 매핑될 키 값
            String mapKey = null;

            switch (itemName) {
                case "현재가":
                    mapKey = "currentPrice";
                    value = firstCell.text().trim();
                    break;
                case "전일대비":
                    mapKey = "changePrice";
                    Element emChangePrice = firstCell.selectFirst("em");
                    value = (emChangePrice != null) ? emChangePrice.text().trim()
                            .replace("하향", "▼").replace("상향", "▲") : firstCell.text().trim();
                    break;
                case "등락률":
                    mapKey = "changeRate";
                    Element emChangeRate = firstCell.selectFirst("em");
                    value = (emChangeRate != null) ? emChangeRate.text().trim()
                            .replace("하향", "").replace("상향", "") : firstCell.text().trim();
                    break;
                case "매출액(억)":
                    mapKey = "salesRevenue";
                    value = firstCell.text().trim();
                    break;
                case "영업이익(억)":
                    mapKey = "operProfit";
                    value = firstCell.text().trim();
                    break;
                case "조정영업이익(억)":
                    mapKey = "adjustedOperProfit";
                    value = firstCell.text().trim();
                    break;
                case "영업이익증가율(%)":
                    mapKey = "operProfitGrowthRate";
                    value = firstCell.text().trim();
                    break;
                case "당기순이익(억)":
                    mapKey = "netIncome";
                    value = firstCell.text().trim();
                    break;
                case "주당순이익(원)":
                    mapKey = "earningPerShare";
                    value = firstCell.text().trim();
                    break;
                case "ROE(%)":
                    mapKey = "roe";
                    value = firstCell.text().trim();
                    break;
                // 이 항목들은 tab_con1에서 가져올 예정이므로 건너뜀
                case "시가총액(억)":
                case "외국인비율(%)":
                case "PER(%)":
                case "PBR(배)":
                    continue;
                default:
                    // 정의되지 않은 항목은 무시하거나, 필요에 따라 처리
                    continue;
            }
            if (mapKey != null) {
                stockDataMap.put(mapKey, value);
            }
        }
    }

    /**
     * tab_con1 영역에서 메인 종목의 추가 상세 정보를 파싱합니다.
     * @param doc Jsoup Document 객체
     * @param stockDataMap 데이터를 저장할 Map
     */
    private static void parseTabCon1Section(Document doc, Map<String, String> stockDataMap) {
        Element tabCon1 = doc.selectFirst("div#tab_con1");
        if (tabCon1 == null) {
            return;
        }

        // 1. 시가총액 정보 테이블
        Element marketSumTable = tabCon1.selectFirst("table[summary='시가총액 정보']");
        if (marketSumTable != null) {
            // 시가총액
            Element marketSumEm = marketSumTable.selectFirst("th:contains(시가총액) + td em");
            if (marketSumEm != null) {
                stockDataMap.put("marketCap", cleanAndCombineText(marketSumEm.parent())); // '억원'까지 포함
            }
            // 시가총액순위
            Element rankTd = marketSumTable.selectFirst("th:contains(시가총액순위) + td");
            if (rankTd != null) {
                stockDataMap.put("marketCapRank", rankTd.text().trim());
            }
            // 상장주식수
            Element listedSharesEm = marketSumTable.selectFirst("th:contains(상장주식수) + td em");
            if (listedSharesEm != null) {
                stockDataMap.put("listedSharesCount", listedSharesEm.text().trim());
            }
            // 액면가 | 매매단위
            Element faceValueTd = marketSumTable.selectFirst("th:contains(액면가) + td");
            if (faceValueTd != null) {
                // '100원 l 1주' 처럼 분리하여 저장하거나 통째로 저장
                String fullText = faceValueTd.text().trim();
                // 정규 표현식을 사용하여 액면가와 매매단위 분리
                Pattern pattern = Pattern.compile("(.+원)\\s*l\\s*(.+주)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    stockDataMap.put("parValue", matcher.group(1));
                    stockDataMap.put("tradingUnit", matcher.group(2));
                } else {
                    stockDataMap.put("parValue", fullText); // 분리 실패 시 전체 저장
                    stockDataMap.put("tradingUnit", null);
                }
            }
        }

        // 2. 투자의견 및 52주 최고/최저 테이블 (class가 gray가 아닌 테이블)
        // div.first 아래의 테이블은 이미 처리했고, class="gray"는 스킵할 것이므로
        // 그 다음 div 바로 아래의 table을 찾음
        Elements opinionTables = tabCon1.select("div:not(.gray) > table[summary='투자의견 정보']");
        if (!opinionTables.isEmpty()) {
            Element opinionTable = opinionTables.first(); // 첫 번째 테이블 사용

            // 투자의견 | 목표주가
            Element opinionTd = opinionTable.selectFirst("th:contains(투자의견) + td");
            if (opinionTd != null) {
                String fullText = opinionTd.text().trim();
                // '4.00매수 l 76,333' 에서 투자의견과 목표주가 분리
                Pattern pattern = Pattern.compile("(.+?)\\s*l\\s*(.+)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    stockDataMap.put("investmentOpinion", matcher.group(1));
                    stockDataMap.put("targetPrice", matcher.group(2));
                } else {
                    stockDataMap.put("investmentOpinion", fullText);
                    stockDataMap.put("targetPrice", null);
                }
            }

            // 52주최고 | 최저
            Element fiftyTwoWeekTd = opinionTable.selectFirst("th:contains(52주최고) + td");
            if (fiftyTwoWeekTd != null) {
                String fullText = fiftyTwoWeekTd.text().trim();
                // '86,100 l 49,900' 에서 최고가와 최저가 분리
                Pattern pattern = Pattern.compile("(.+?)\\s*l\\s*(.+)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    stockDataMap.put("fiftyTwoWeekHigh", matcher.group(1));
                    stockDataMap.put("fiftyTwoWeekLow", matcher.group(2));
                } else {
                    stockDataMap.put("fiftyTwoWeekHigh", fullText);
                    stockDataMap.put("fiftyTwoWeekLow", null);
                }
            }
        }

        // 3. PER/EPS 정보 테이블
        Element perEpsTable = tabCon1.selectFirst("table.per_table[summary='PER/EPS 정보']");
        if (perEpsTable != null) {
            // PER | EPS (현재)
            Element perEpsTd = perEpsTable.selectFirst("th:contains(PER) + td");
            if (perEpsTd != null) {
                String fullText = perEpsTd.text().trim();
                Pattern pattern = Pattern.compile("(.+?배)\\s*l\\s*(.+원)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    stockDataMap.put("currentPer", matcher.group(1));
                    stockDataMap.put("currentEps", matcher.group(2));
                } else {
                    stockDataMap.put("currentPer", fullText);
                    stockDataMap.put("currentEps", null);
                }
            }
            // PBR | BPS
            Element pbrBpsTd = perEpsTable.selectFirst("th:contains(PBR) + td");
            if (pbrBpsTd != null) {
                String fullText = pbrBpsTd.text().trim();
                Pattern pattern = Pattern.compile("(.+?배)\\s*l\\s*(.+원)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    stockDataMap.put("pbr", matcher.group(1));
                    stockDataMap.put("bps", matcher.group(2));
                } else {
                    stockDataMap.put("pbr", fullText);
                    stockDataMap.put("bps", null);
                }
            }
            // 배당수익률
            Element dividendYieldTd = perEpsTable.selectFirst("th:contains(배당수익률) + td em");
            if (dividendYieldTd != null) {
                stockDataMap.put("dividendYield", dividendYieldTd.text().trim());
            }
        }
    }

    /**
     * Map<String, String> 에서 StockPriceDto 객체로 데이터를 매핑합니다.
     * 숫자 형식의 문자열에서 쉼표나 한글 단위를 제거하고 적절한 타입으로 변환합니다.
     * @param dataMap 파싱된 데이터가 담긴 Map
     * @return StockPriceDto 객체
     */
    private static StockPriceDto mapToStockPriceDto(Map<String, String> dataMap) {
        StockPriceDto dto = new StockPriceDto();

        // ----------------------------------------------------
        // 문자열 필드 매핑
        // ----------------------------------------------------
        dto.setStockCode(dataMap.get("stockCode"));
        dto.setStockNm(dataMap.get("stockNm"));
        dto.setChangePrice(dataMap.get("changePrice")); // 예: "하락 1,100"
        dto.setChangeRate(dataMap.get("changeRate"));   // 예: "하락 -1.56%"
        dto.setOperProfitGrowthRate(dataMap.get("operProfitGrowthRate")); // 예: "2.97"
        dto.setEarningPerShare(dataMap.get("earningPerShare")); // 예: "1,186.35"
        dto.setRoe(dataMap.get("roe")); // 예: "9.24"
        dto.setMarketCap(dataMap.get("marketCap")); // 예: "416조 4,465억원"
        dto.setMarketCapRank(dataMap.get("marketCapRank")); // 예: "코스피 1위"
        dto.setInvestmentOpinion(dataMap.get("investmentOpinion")); // 예: "4.00매수"
        dto.setCurrentPer(dataMap.get("currentPer")); // 예: "13.68배"
        dto.setPbr(dataMap.get("pbr")); // 예: "1.20배"
        dto.setDividendYield(dataMap.get("dividendYield")); // 예: "2.05%"

        // ----------------------------------------------------
        // 숫자 필드 변환 및 매핑 (문자열 클리닝 필수)
        // ----------------------------------------------------
        dto.setCurrentPrice(parseInteger(dataMap.get("currentPrice")));
        dto.setSalesRevenue(parseInteger(dataMap.get("salesRevenue")));
        dto.setOperProfit(parseInteger(dataMap.get("operProfit")));
        dto.setAdjustedOperProfit(parseInteger(dataMap.get("adjustedOperProfit")));
        dto.setNetIncome(parseInteger(dataMap.get("netIncome")));
        dto.setTargetPrice(parseInteger(dataMap.get("targetPrice")));
        dto.setFiftyTwoWeekHigh(parseInteger(dataMap.get("fiftyTwoWeekHigh")));
        dto.setFiftyTwoWeekLow(parseInteger(dataMap.get("fiftyTwoWeekLow")));
        dto.setCurrentEps(parseInteger(dataMap.get("currentEps")));
        dto.setBps(parseInteger(dataMap.get("bps")));
        dto.setParValue(parseInteger(dataMap.get("parValue"))); // "100원"에서 "원" 제거
        dto.setTradingUnit(parseInteger(dataMap.get("tradingUnit"))); // "1주"에서 "주" 제거

        // Long 타입
        dto.setListedSharesCount(parseLong(dataMap.get("listedSharesCount")));

        // ----------------------------------------------------
        // 현재 HTML에 없는 필드 초기화 (필요시 별도 파싱 로직 추가)
        // ----------------------------------------------------
        dto.setOpeningPrice(null);
        dto.setHighPrice(null);
        dto.setLowPrice(null);
        dto.setEndingPrice(null);


        // ----------------------------------------------------
        // 상태 및 시간 설정
        // ----------------------------------------------------
        dto.setStatusCode(200); // 성공 코드 (예시)
        dto.setErrorMessage(null); // 에러 메시지 없음
        dto.setCollectedAt(LocalDateTime.now()); // 현재 시간 설정

        return dto;
    }

    /**
     * 문자열에서 숫자만 추출하여 Integer로 변환 (쉼표, 한글 단위 제거)
     * @param text 변환할 문자열
     * @return Integer 값 또는 null (변환 실패 시)
     */
    private static Integer parseInteger(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        // 숫자, 소수점, 마이너스 기호만 남기고 모두 제거
        // "1,186.35" -> "1186" (소수점 이하 버림)
        // "100원" -> "100"
        // "76,333" -> "76333"
        String cleanedText = text.replaceAll("[^0-9\\-]", "");
        try {
            return Integer.parseInt(cleanedText);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 문자열에서 숫자만 추출하여 Long으로 변환 (쉼표, 한글 단위 제거)
     * @param text 변환할 문자열
     * @return Long 값 또는 null (변환 실패 시)
     */
    private static Long parseLong(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String cleanedText = text.replaceAll("[^0-9\\-]", "");
        try {
            return Long.parseLong(cleanedText);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 주어진 요소의 모든 자식 텍스트 노드와 자식 요소의 텍스트를 결합하여 반환합니다.
     * 불필요한 공백을 제거하고, 특정 태그(예: <br>)는 제외합니다.
     * @param element 텍스트를 추출할 부모 Element
     * @return 결합된 텍스트
     */
    private static String cleanAndCombineText(Element element) {
        if (element == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (org.jsoup.nodes.Node node : element.childNodes()) {
            if (node instanceof org.jsoup.nodes.TextNode) {
                sb.append(((org.jsoup.nodes.TextNode) node).text());
            } else if (node instanceof Element) {
                Element childElement = (Element) node;
                // <br> 태그 등은 스킵
                if (!childElement.tagName().equals("br")) {
                    sb.append(cleanAndCombineText(childElement)); // 재귀적으로 자식 요소의 텍스트도 가져옴
                }
            }
        }
        // 여러 공백을 하나의 공백으로 줄이고, 앞뒤 공백 제거
        return sb.toString().replaceAll("\\s+", " ").trim();
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

    // CNN 기사 크롤링 진입점 메서드
    @Override
    public void articleCrawling(String startUrl, int maxDepth) {
        Queue<String> articleUrlsToCrawl = new LinkedList<>();
        Set<String> visitedArticleUrls = new HashSet<>();

        collectArticleUrlsFromMainPage(startUrl, articleUrlsToCrawl, visitedArticleUrls);

        crawlAndSaveArticleDetails(articleUrlsToCrawl, visitedArticleUrls);
    }

    /**
     * 1단계: CNN 메인 페이지에서 기사 링크들을 수집하여 큐에 추가합니다.
     *
     * @param mainPageUrl        크롤링을 시작할 메인 페이지 URL
     * @param articleUrlsToCrawl 기사 URL을 담을 큐
     * @param visitedArticleUrls 방문했거나 방문 예정인 기사 URL을 기록할 Set (중복 방지용)
     */
    private void collectArticleUrlsFromMainPage(String mainPageUrl,
                                                Queue<String> articleUrlsToCrawl,
                                                Set<String> visitedArticleUrls) {
        Document mainPageDoc = null;
        try {
            Connection.Response response = Jsoup.connect(mainPageUrl)
                    .userAgent(USER_AGENT)
                    .referrer(REFERRER)
                    .timeout(TIME_OUT)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();

            int statusCode = response.statusCode();
            String contentType = response.contentType();

            // HTTP 200 ok인 경우에만 파싱
            if (statusCode == 200 && contentType != null && contentType.startsWith("text/html")) {
                mainPageDoc = response.parse();

                Elements linkElements = mainPageDoc.select("a.container__link.container__link--type-article[href]");
                for (Element linkElement : linkElements) {
                    String absUrl = linkElement.attr("abs:href");

                    // 유효하지 않은 링크 스킵 (mailto, tel, javascript 등)
                    if (absUrl.isEmpty() || absUrl.startsWith("#") || absUrl.startsWith("mailto:") || absUrl.startsWith("tel:") || absUrl.startsWith("javascript:")) {
                        continue;
                    }

                    // CNN 기사 URL 패턴 필터링 (날짜 패턴 포함)
                    // 예: https://edition.cnn.com/2025/08/01/politics/some-article-title/index.html
                    if (absUrl.matches(".*cnn\\.com/\\d{4}/\\d{2}/\\d{2}/.*")) {
                        // 중복 체크: 이미 큐에 있거나 처리된 URL인지 확인
                        if (visitedArticleUrls.add(absUrl)) { // add()는 추가 성공 시 true 반환 (즉, 이전에 없었다는 뜻)
                            articleUrlsToCrawl.add(absUrl);
                        }
                    }
                }
            } else {
                LogUtil.logError("메인 페이지 접속 실패 또는 HTML 아님: " + mainPageUrl + " - Status: " + statusCode + ", Content-Type: " + contentType, null);
            }
        } catch (Exception e) {
            LogUtil.logError("메인 페이지 크롤링 중 알 수 없는 오류: " + mainPageUrl + " - " + e.getMessage(), e);
        }
    }

    /**
     * 2단계: 수집된 기사 URL 큐를 순회하며 각 기사 본문을 크롤링하고 DB에 저장합니다.
     *
     * @param articleUrlsToCrawl 기사 URL을 담은 큐
     * @param visitedArticleUrls 방문했거나 방문 예정인 기사 URL을 기록할 Set (여기서는 주로 로깅/확인용)
     */
    private void crawlAndSaveArticleDetails(Queue<String> articleUrlsToCrawl,
                                            Set<String> visitedArticleUrls) {
        while (!articleUrlsToCrawl.isEmpty()) {
            String currentArticleUrl = articleUrlsToCrawl.poll();

            if (cnnArticleRepository.findByArticleUrl(currentArticleUrl).isPresent()) {
                continue;
            }

            // 랜덤 딜레이 적용
            try {
                long delay = MIN_DELAY_MS + random.nextInt((int) (MAX_DELAY_MS - MIN_DELAY_MS + 1));
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LogUtil.logError("크롤링 딜레이 중 인터럽트 발생: " + e.getMessage(), e);
                return;
            }

            CnnArticleDto articleDto = new CnnArticleDto();
            articleDto.setArticleUrl(currentArticleUrl);

            try {
                Connection.Response response = Jsoup.connect(currentArticleUrl)
                        .userAgent(USER_AGENT)
                        .referrer(CNN_REFERRER)
                        .timeout(TIME_OUT)
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .execute();

                int statusCode = response.statusCode();
                String contentType = response.contentType();
                articleDto.setStatusCode(statusCode);

                if (statusCode == 200 && contentType != null && contentType.startsWith("text/html")) {
                    Document articleDoc = response.parse();
                    // ----------------------------------------------------
                    // 기사 데이터 파싱 및 추출 (정확한 셀렉터 확인 필요)
                    // ----------------------------------------------------
                    String title = null;
                    String content = "";
                    String author = null;
                    LocalDateTime publishedAt = null;

                    Element titleElement = articleDoc.selectFirst("h1.headline__text");
                    if (titleElement != null) {
                        title = titleElement.text().trim();
                    }

                    StringBuilder sb = new StringBuilder();
                    Elements contentElements = articleDoc.select("div.article__content > p[data-component-name='paragraph']");
                    for (Element contentElement : contentElements) {
                        String paragraphText = contentElement.text().trim();
                        if (!paragraphText.isEmpty()) {
                            sb.append(paragraphText).append("\n\n");
                        }
                    }
                    content = sb.toString().trim();

                    Element authorElement = articleDoc.selectFirst("span.byline__name");
                    if (authorElement != null) {
                        author = authorElement.ownText().trim();
                    }

                    Element publishedAtElement = articleDoc.selectFirst("div.timestamp__published");
                    if (publishedAtElement != null) {
                        String cleanedDateTime = publishedAtElement.text().replace("PUBLISHED", "").replace(" ET", "").trim();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.ENGLISH);

                        try {
                            // 파싱된 결과를 LocalDateTime으로 변환
                            publishedAt = LocalDateTime.parse(cleanedDateTime, formatter);
                        } catch (DateTimeParseException e) {
                            publishedAt = LocalDateTime.now();
                            LogUtil.logError("날짜/시간 파싱 오류: " + currentArticleUrl + " - " + e.getMessage() + " (Original: " + publishedAtElement.text() + ")", e);
                        }
                    }

                    articleDto.setTitle(title);
                    if (content == null) {
                        articleDto.setContent("");
                    } else {
                        articleDto.setContent(content);
                    }
                    articleDto.setAuthor(author);
                    articleDto.setErrorMessage(null);
                    articleDto.setCrawledAt(LocalDateTime.now());
                    articleDto.setPublishedAt(publishedAt);

                    cnnArticleRepository.save(CnnArticle.toEntity(articleDto));

                } else {
                    LogUtil.logError("  [기사 크롤링 실패] " + currentArticleUrl + " - " + articleDto.getErrorMessage(), null);
                }
            } catch (HttpStatusException e) {
                LogUtil.logError("  [기사 크롤링 실패] " + currentArticleUrl + " - HTTP 오류: " + e.getStatusCode(), e);

            } catch (Exception e) {
                LogUtil.logError("  [기사 크롤링 실패] " + currentArticleUrl + " - 알 수 없는 오류: " + e.getMessage(), e);

            }
        }
    }
}
