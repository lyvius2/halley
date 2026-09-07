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

/** 네이버 뉴스 검색 어댑터. */
@Slf4j
@Component
public class NaverNewsAdapter implements NewsSearchPort {

 /** Sun, 12 Jul 2026 09:00:00 +0900. RFC 1123에서 요일을 뺀 부분만 읽습니다. */
    private static final DateTimeFormatter PUB_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
 /** Sun, 처럼 앞에 붙는 요일. */
    private static final java.util.regex.Pattern WEEKDAY =
            java.util.regex.Pattern.compile("^[A-Za-z]{3},\\s*");

 /** 기사는 하루 사이에 달라지지 않는다. */
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
        final String body = client.searchNews(clientId, clientSecret, query.trim(), limit, "date");
        if (body == null) {
            return List.of();
        }
        final Optional<List<NewsArticle>> answered = tryParse(body, query);
        answered.ifPresent(articles -> write(key, articles));
        return answered.orElseGet(List::of);
    }

 /** 검색어가 같으면 결과도 같다. */
    private String cacheKey(String query, int limit) {
        return query.trim().replaceAll("\\s+", " ") + "|" + limit;
    }

    private Optional<List<NewsArticle>> read(String key) {
        try {
            return cache.get(CachePort.NEWS, key)
                    .map(json -> objectMapper.readValue(json, ARTICLES));
        } catch (RuntimeException e) {
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

 /** 네이버가 답을 준 것인가. */
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
                    firstNonBlank(item.path("originallink").asString(null),
                            item.path("link").asString(null)),
                    sourceOf(item.path("originallink").asString(null)),
                    publishedOn(item.path("pubDate").asString(null))));
        }
        log.info("News articles found. query={}, count={}", query, articles.size());
        return Optional.of(articles);
    }

 /** 검색어가 …로 감싸여 옵니다. 그대로 화면에 넣으면 안 됩니다. */
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

 /** https://www.hankyung.com/article/… → hankyung.com. */
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
