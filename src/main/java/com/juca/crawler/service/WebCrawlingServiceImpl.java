package com.juca.crawler.service;

import com.juca.crawler.domain.CrawledPage;
import com.juca.crawler.domain.ExtractedLink;
import com.juca.crawler.domain.StockPrice;
import com.juca.crawler.dto.CrawledPageDto;
import com.juca.crawler.dto.ExtractedLinkDto;
import com.juca.crawler.dto.StockPriceDto;
import com.juca.crawler.repository.CrawledPageRepository;
import com.juca.crawler.repository.ExtractedLinkRepository;
import com.juca.crawler.repository.StockPriceRepository;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WebCrawlingServiceImpl implements WebCrawlingService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36";
    private static final int TIME_OUT = 10000;
    private static final String REFERRER = "https://www.naver.com";

    private final CrawledPageRepository crawledPageRepository;
    private final ExtractedLinkRepository extractedLinkRepository;
    private final StockPriceRepository stockPriceRepository;

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

                    // 테이블 데이터를 저장할 리스트
                    Map<String, String> stockData = new LinkedHashMap<>();

//                  ==============================================================================

                    // ----------------------------------------------------
                    // 1. 동종업종 비교 테이블에서 첫 번째 종목 정보 파싱
                    // ----------------------------------------------------
                    parseComparativeTable(doc, stockData);

                    // ----------------------------------------------------
                    // 2. tab_con1 영역에서 추가 정보 파싱
                    // ----------------------------------------------------
                    parseTabCon1Section(doc, stockData);

                    System.out.println("--- 파싱된 종목 데이터 ---");
                    for (Map.Entry<String, String> entry : stockData.entrySet()) {
                        System.out.println(entry.getKey() + ": " + entry.getValue());
                    }



//                    StockPriceDto stockPriceDto = new StockPriceDto();
//                    stockPriceDto.setStockCode(stockCode);
//                    stockPriceDto.setStockNm(stockName);
//                    stockPriceDto.setCurrentPrice(convertToInt(currentPrice));
//                    stockPriceDto.setChangePrice(convertToInt(changePrice));
//                    stockPriceDto.setChangeRatio(changeRatio);
//                    stockPriceDto.setTradeVolume(convertToLong(tradeVolume));
//                    stockPriceDto.setTradingValue(convertToLong(tradeValue));
//                    stockPriceDto.setOpeningPrice(convertToInt(openingPrice));
//                    stockPriceDto.setHighPrice(convertToInt(highPrice));
//                    stockPriceDto.setLowPrice(convertToInt(lowPrice));
//                    stockPriceDto.setEndingPrice(convertToInt(prevClosingPrice));
//                    stockPriceDto.setMarketCap(marketCap);
//                    stockPriceDto.setMarketCapRank(marketCapRank);
//                    stockPriceDto.setListedSharesCount(convertToLong(listedSharesCount));
//                    stockPriceDto.setParValue(convertToInt(parValue));
//                    stockPriceDto.setTradingUnit(convertToInt(tradingUnit));
//                    stockPriceDto.setInvestmentOpinion(investmentOpinion);
//                    stockPriceDto.setTargetPrice(convertToInt(targetPrice));
//                    stockPriceDto.setFiftyTwoWeekHigh(convertToInt(fiftyTwoWeekHigh));
//                    stockPriceDto.setFiftyTwoWeekLow(convertToInt(fiftyTwoWeekLow));
//                    stockPriceDto.setCurrentPer(currentPER);
//                    stockPriceDto.setCurrentEps(convertToInt(currentEPS));
//                    stockPriceDto.setPbr(pbr);
//                    stockPriceDto.setBps(convertToInt(bps));
//                    stockPriceDto.setDividendYield(dividendYield);
//                    stockPriceDto.setIndustryPer(industryPER);
//                    stockPriceDto.setIndustryFluctuationRate(industryFluctuationRate);
//                    stockPriceDto.setStatusCode(statusCode);
//
//                    StockPrice entity = StockPrice.dtoToEntity(stockPriceDto);
//                    stockPriceRepository.save(entity);
                }
            } catch (Exception e) {
                StockPriceDto stockPriceDto = new StockPriceDto();
                stockPriceDto.setErrorMessage(e.getMessage());

                StockPrice entity = StockPrice.dtoToEntity(stockPriceDto);
                stockPriceRepository.save(entity);
            }
        }
    }

    /**
     * 동종업종 비교 테이블에서 첫 번째 종목 (검색한 종목)의 정보를 파싱합니다.
     * @param doc Jsoup Document 객체
     * @param stockData 데이터를 저장할 Map
     */
    private static void parseComparativeTable(Document doc, Map<String, String> stockData) {
        Element table = doc.selectFirst("table.tb_type1.tb_num[summary*='동종업종 비교']");

        if (table == null) {
            System.out.println("DEBUG: 동종업종 비교 테이블을 찾을 수 없습니다.");
            return;
        }

        // 종목명과 코드 추출 (첫 번째 종목)
        Element firstStockHeader = table.selectFirst("thead tr th[scope='col']");
        if (firstStockHeader != null) {
            Element link = firstStockHeader.selectFirst("a");
            if (link != null) {
                stockData.put("종목명", link.ownText().trim()); // 링크 자체 텍스트 (삼성전자)
                Element codeEm = link.selectFirst("em");
                if (codeEm != null) {
                    stockData.put("종목코드", codeEm.text().trim()); // 005930
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

            // 시가총액, 외국인비율, PER, PBR은 이 테이블에서 가져오지 않음 (아래 tab_con1에서 가져올 예정)
            if (itemName.equals("시가총액(억)") || itemName.equals("외국인비율(%)") ||
                    itemName.equals("PER(%)") || itemName.equals("PBR(배)")) {
                continue; // 이 항목들은 건너뛰고 다음 테이블에서 가져올 것임
            }

            // '전일대비'와 '등락률' 항목 특수 처리
            if (itemName.equals("전일대비") || itemName.equals("등락률")) {
                Element emElement = firstCell.selectFirst("em");
                if (emElement != null) {
                    // '하향' 또는 '상향' 텍스트를 '하락' 또는 '상승'으로 변경하여 포함
                    value = emElement.text().trim()
                            .replace("하향", "하락")
                            .replace("상향", "상승");
                } else {
                    value = firstCell.text().trim();
                }
            } else {
                // 그 외의 항목들은 td의 직접적인 텍스트를 가져옴
                value = firstCell.text().trim();
            }

            stockData.put(itemName, value);
        }
    }

    /**
     * tab_con1 영역에서 메인 종목의 추가 상세 정보를 파싱합니다.
     * @param doc Jsoup Document 객체
     * @param stockData 데이터를 저장할 Map
     */
    private static void parseTabCon1Section(Document doc, Map<String, String> stockData) {
        Element tabCon1 = doc.selectFirst("div#tab_con1");
        if (tabCon1 == null) {
            System.out.println("DEBUG: tab_con1 섹션을 찾을 수 없습니다.");
            return;
        }

        // 1. 시가총액 정보 테이블
        Element marketSumTable = tabCon1.selectFirst("table[summary='시가총액 정보']");
        if (marketSumTable != null) {
            // 시가총액
            Element marketSumEm = marketSumTable.selectFirst("th:contains(시가총액) + td em");
            if (marketSumEm != null) {
                stockData.put("시가총액", cleanAndCombineText(marketSumEm.parent())); // '억원'까지 포함
            }
            // 시가총액순위
            Element rankTd = marketSumTable.selectFirst("th:contains(시가총액순위) + td");
            if (rankTd != null) {
                stockData.put("시가총액순위", rankTd.text().trim());
            }
            // 상장주식수
            Element listedSharesEm = marketSumTable.selectFirst("th:contains(상장주식수) + td em");
            if (listedSharesEm != null) {
                stockData.put("상장주식수", listedSharesEm.text().trim());
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
                    stockData.put("액면가", matcher.group(1));
                    stockData.put("매매단위", matcher.group(2));
                } else {
                    stockData.put("액면가/매매단위", fullText); // 분리 실패 시 전체 저장
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
                    stockData.put("투자의견", matcher.group(1));
                    stockData.put("목표주가", matcher.group(2));
                } else {
                    stockData.put("투자의견/목표주가", fullText);
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
                    stockData.put("52주최고", matcher.group(1));
                    stockData.put("52주최저", matcher.group(2));
                } else {
                    stockData.put("52주최고/최저", fullText);
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
                    stockData.put("PER", matcher.group(1));
                    stockData.put("EPS", matcher.group(2));
                } else {
                    stockData.put("PER/EPS", fullText);
                }
            }
            // PBR | BPS
            Element pbrBpsTd = perEpsTable.selectFirst("th:contains(PBR) + td");
            if (pbrBpsTd != null) {
                String fullText = pbrBpsTd.text().trim();
                Pattern pattern = Pattern.compile("(.+?배)\\s*l\\s*(.+원)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    stockData.put("PBR", matcher.group(1));
                    stockData.put("BPS", matcher.group(2));
                } else {
                    stockData.put("PBR/BPS", fullText);
                }
            }
            // 배당수익률
            Element dividendYieldTd = perEpsTable.selectFirst("th:contains(배당수익률) + td em");
            if (dividendYieldTd != null) {
                stockData.put("배당수익률", dividendYieldTd.text().trim());
            }
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

    private int convertToInt(String str) {

        String cleanStr = str.replaceAll("^[0-9]+", "").trim();
        return Integer.parseInt(cleanStr);
    }

    private Long convertToLong(String str) {
        String cleanStr = str.replaceAll("^[0-9]+", "").trim();
        return Long.parseLong(cleanStr);
    }

    // --- 헬퍼 함수: 자식 span 태그들의 텍스트를 합쳐서 반환 ---
    private static String getCombinedSpanText(Element parentElement) {
        if (parentElement == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        // 모든 자식 노드를 순회하여 텍스트 노드나 span 태그의 텍스트를 가져옴
        for (org.jsoup.nodes.Node node : parentElement.childNodes()) {
            if (node instanceof org.jsoup.nodes.TextNode) {
                String text = ((org.jsoup.nodes.TextNode) node).text().trim();
                if (!text.isEmpty()) {
                    sb.append(text);
                }
            } else if (node instanceof Element) {
                Element childElement = (Element) node;
                // ico 클래스는 '하락', '상승' 같은 아이콘 텍스트이므로 제외하거나 필요에 따라 별도 처리
                // 여기서는 숫자 관련 span만 포함하도록 필터링
                if (childElement.tagName().equals("span") && !childElement.hasClass("ico")) {
                    sb.append(childElement.text().trim());
                }
            }
        }
        return sb.toString().replaceAll("\\s+", ""); // 모든 공백 제거
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
