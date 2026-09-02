package banghak.home.halley.adapter.outbound.external.naver;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.application.port.out.external.NewsSearchPort;
import banghak.home.halley.domain.news.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import tools.jackson.core.type.TypeReference;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 네이버 뉴스 검색 어댑터 (설계 I137).
 *
 * <p><b>응답에 HTML 태그가 섞여 옵니다.</b> `title`·`description`의 검색어가
 * `<b>…</b>`로 감싸여 오는데, 그대로 화면에 넣으면 <b>HTML 주입 통로</b>가 됩니다.
 * 여기서 걷어 내고 텍스트로만 다룹니다.
 *
 * <h4>하루 담아 둡니다 (설계 I246)</h4>
 *
 * <p>상세를 열 때마다 물었습니다. 같은 매물을 몇 번 열든 <b>기사는 그대로</b>이고,
 * 네이버 검색은 일일 호출 한도가 있습니다.
 *
 * <p>키는 <b>검색어</b>입니다. 매물 번호가 아닙니다 — 같은 단지의 매물 둘은 검색어가
 * 같고, 그러면 한 번만 물으면 됩니다.
 *
 * <p><b>실패는 담지 않습니다.</b> 이것이 이 캐시에서 가장 조심할 자리입니다:
 * 키가 없거나 폴백이 돌거나 응답이 깨졌을 때도 빈 목록이 나가는데, 그것까지 담으면
 * <b>네이버가 잠깐 죽은 것 때문에 하루 종일 "기사 없음"</b>이 됩니다.
 * <b>진짜로 기사가 없는 것</b>만 담습니다 — 그건 담아야 합니다. 안 그러면 결과가
 * 없는 단지에서 캐시가 아무 일도 안 합니다([I219]와 같은 처방).
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

    /** 기사는 하루 사이에 달라지지 않는다 (설계 I246). */
    private static final Duration TTL = Duration.ofHours(24);

    private static final TypeReference<List<NewsArticle>> ARTICLES = new TypeReference<>() { };

    private final NaverSearchFeignClient client;
    private final ObjectMapper objectMapper;
    private final CachePort cache;
    private final String clientId;
    private final String clientSecret;

    public NaverNewsAdapter(NaverSearchFeignClient client,
                            ObjectMapper objectMapper,
                            CachePort cache,
                            @Value("${naver.client-id:}") String clientId,
                            @Value("${naver.client-secret:}") String clientSecret) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.cache = cache;
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
        final String key = cacheKey(query, limit);
        final Optional<List<NewsArticle>> held = read(key);
        if (held.isPresent()) {
            return held.get();
        }
        // 개발 호재는 최신순이 맞다 — 정확도순은 오래된 기사가 위로 온다
        final String body = client.searchNews(clientId, clientSecret, query.trim(), limit, "date");
        if (body == null) {
            // 폴백이 돌았다 (설계 I246). <b>담지 않습니다</b> — 잠깐 죽은 것을
            // 하루짜리 "기사 없음"으로 굳히면 안 됩니다
            return List.of();
        }
        // 빈 Optional 이면 <b>못 읽은 것</b>이다. 담지 않는다 (설계 I246)
        final Optional<List<NewsArticle>> answered = tryParse(body, query);
        answered.ifPresent(articles -> write(key, articles));
        return answered.orElseGet(List::of);
    }

    /**
     * 검색어가 같으면 결과도 같다 (설계 I246).
     *
     * <p>{@code limit} 을 넣는 이유는 설정을 바꿨을 때 <b>옛 개수로 담아 둔 것</b>이
     * 그대로 나오면 안 되기 때문입니다.
     */
    private String cacheKey(String query, int limit) {
        return query.trim().replaceAll("\\s+", " ") + "|" + limit;
    }

    private Optional<List<NewsArticle>> read(String key) {
        try {
            return cache.get(CachePort.NEWS, key)
                    .map(json -> objectMapper.readValue(json, ARTICLES));
        } catch (RuntimeException e) {
            // 못 읽으면 그냥 물어보면 됩니다 — 캐시 때문에 기능이 죽을 이유는 없습니다
            log.warn("News cache read failed - asking Naver instead. key={}, cause={}",
                    key, e.getMessage());
            return Optional.empty();
        }
    }

    private void write(String key, List<NewsArticle> articles) {
        try {
            cache.put(CachePort.NEWS, key, objectMapper.writeValueAsString(articles), TTL);
        } catch (RuntimeException e) {
            log.warn("News cache write failed - the next open will ask Naver again. "
                    + "key={}, cause={}", key, e.getMessage());
        }
    }

    List<NewsArticle> parse(String body, String query) {
        return tryParse(body, query).orElseGet(List::of);
    }

    /**
     * 네이버가 <b>답을 준 것인가</b> (설계 I246).
     *
     * <p>빈 목록이 두 가지 뜻입니다 — <b>기사가 없다</b>와 <b>못 읽었다.</b>
     * 둘 다 {@code List.of()} 로 두면 구분이 사라지고, 그러면 못 읽은 것을
     * 하루 동안 "기사 없음"으로 굳힙니다.
     *
     * <p>빈 {@code Optional} = 못 읽었다. 빈 <b>목록</b> = 기사가 없다 —
     * <b>이것은 담습니다.</b> 기사가 없는 단지는 앞으로도 없고, 안 담으면 그 매물은
     * 상세를 열 때마다 네이버를 부릅니다([I219]와 같은 처방).
     */
    private Optional<List<NewsArticle>> tryParse(String body, String query) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            log.warn("Failed to parse Naver news response. query={}, cause={}", query, e.getMessage());
            return Optional.empty();
        }
        final JsonNode items = root.path("items");
        if (!items.isArray()) {
            // 오류도 200으로 올 수 있다. errorMessage 가 있으면 그것을 남긴다
            final String error = root.path("errorMessage").asString(null);
            if (error != null) {
                log.warn("Naver news search rejected. query={}, errorCode={}, message={}",
                        query, root.path("errorCode").asString(null), error);
            }
            return Optional.empty();
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
        return Optional.of(articles);
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
