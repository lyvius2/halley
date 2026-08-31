package banghak.home.halley.adapter.outbound.external.naver;

import banghak.home.halley.application.port.out.external.NewsSearchPort;
import banghak.home.halley.domain.news.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 네이버 뉴스 검색 어댑터 (설계 I137).
 *
 * <p><b>응답에 HTML 태그가 섞여 옵니다.</b> `title`·`description`의 검색어가
 * `<b>…</b>`로 감싸여 오는데, 그대로 화면에 넣으면 <b>HTML 주입 통로</b>가 됩니다.
 * 여기서 걷어 내고 텍스트로만 다룹니다.
 */
@Slf4j
@Component
public class NaverNewsAdapter implements NewsSearchPort {

    /**
     * `Sun, 12 Jul 2026 09:00:00 +0900` — RFC 1123에서 <b>요일을 뺀</b> 부분만 읽습니다.
     *
     * <p>요일은 <b>중복 정보</b>입니다. 날짜에서 계산할 수 있고, 그럼에도 파싱에 넣으면
     * 보낸 쪽 요일이 하루라도 어긋났을 때 <b>날짜를 통째로 잃습니다.</b>
     * 우리가 쓰는 것은 날짜뿐이니 요일은 떼고 읽습니다.
     */
    private static final DateTimeFormatter PUB_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
    /** `Sun, ` 처럼 앞에 붙는 요일. */
    private static final java.util.regex.Pattern WEEKDAY =
            java.util.regex.Pattern.compile("^[A-Za-z]{3},\\s*");

    private final NaverSearchFeignClient client;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;

    public NaverNewsAdapter(NaverSearchFeignClient client,
                            ObjectMapper objectMapper,
                            @Value("${naver.client-id:}") String clientId,
                            @Value("${naver.client-secret:}") String clientSecret) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public boolean isEnabled() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public List<NewsArticle> search(String query, int limit) {
        if (!isEnabled()) {
            log.info("Skipping news search - Naver keys not configured.");
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // 개발 호재는 최신순이 맞다 — 정확도순은 오래된 기사가 위로 온다
        final String body = client.searchNews(clientId, clientSecret, query.trim(), limit, "date");
        if (body == null) {
            return List.of();
        }
        return parse(body, query);
    }

    List<NewsArticle> parse(String body, String query) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            log.warn("Failed to parse Naver news response. query={}, cause={}", query, e.getMessage());
            return List.of();
        }
        final JsonNode items = root.path("items");
        if (!items.isArray()) {
            // 오류도 200으로 올 수 있다. errorMessage 가 있으면 그것을 남긴다
            final String error = root.path("errorMessage").asString(null);
            if (error != null) {
                log.warn("Naver news search rejected. query={}, errorCode={}, message={}",
                        query, root.path("errorCode").asString(null), error);
            }
            return List.of();
        }
        final List<NewsArticle> articles = new ArrayList<>();
        for (final JsonNode item : items) {
            final String title = stripTags(item.path("title").asString(null));
            if (title == null || title.isBlank()) {
                continue;
            }
            articles.add(new NewsArticle(
                    title,
                    // 네이버 뉴스 링크가 아니라 원문을 준다 — 원 언론사를 보여 주려는 것
                    firstNonBlank(item.path("originallink").asString(null),
                            item.path("link").asString(null)),
                    sourceOf(item.path("originallink").asString(null)),
                    publishedOn(item.path("pubDate").asString(null))));
        }
        log.info("News articles found. query={}, count={}", query, articles.size());
        return articles;
    }

    /**
     * 검색어가 {@code <b>…</b>}로 감싸여 옵니다. <b>그대로 화면에 넣으면 안 됩니다.</b>
     * HTML 엔티티도 함께 되돌립니다.
     */
    static String stripTags(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("<[^>]*>", "")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .trim();
    }

    /** `https://www.hankyung.com/article/…` → `hankyung.com`. */
    private String sourceOf(String originalLink) {
        if (originalLink == null || originalLink.isBlank()) {
            return null;
        }
        try {
            final String host = URI.create(originalLink).getHost();
            return host == null ? null : host.replaceFirst("^www\\.", "");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private LocalDate publishedOn(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(
                    WEEKDAY.matcher(pubDate.trim()).replaceFirst(""), PUB_DATE).toLocalDate();
        } catch (RuntimeException e) {
            log.warn("Malformed pubDate in news response. value={}", pubDate);
            return null;
        }
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
