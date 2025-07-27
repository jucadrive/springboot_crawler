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
                if (contentType != null && contentType.startsWith("text/html") && statusCode ==200) {
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
                    long delay = 1000 + (long)(Math.random() * 3000); // 1초 ~ 4초 랜덤 딜레이
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
                Connection.Response response = Jsoup.connect(startUrl)
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
                    doc = Jsoup.parse(htmlContent, currentUrl);
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
