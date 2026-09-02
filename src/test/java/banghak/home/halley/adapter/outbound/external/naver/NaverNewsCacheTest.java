package banghak.home.halley.adapter.outbound.external.naver;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.news.NewsArticle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 기사를 하루 담아 둔다 (설계 I246).
 *
 * <p>상세를 열 때마다 물었습니다. 같은 매물을 몇 번 열든 <b>기사는 그대로</b>이고,
 * 네이버 검색은 일일 호출 한도가 있습니다.
 *
 * <h4>이 테스트가 진짜로 보는 것</h4>
 *
 * <p>담아 두기의 위험은 느려지는 것이 아니라 <b>실패를 굳히는 것</b>입니다.
 * 키가 없거나 폴백이 돌거나 응답이 깨졌을 때도 빈 목록이 나가는데, 그것까지 담으면
 * <b>네이버가 잠깐 죽은 것 때문에 하루 종일 "기사 없음"</b>이 됩니다.
 */
@DisplayName("관련 기사 캐시 (설계 I246)")
class NaverNewsCacheTest {

    private static final String BODY = """
            {"items":[
              {"title":"<b>동탄역</b> 복합환승센터 착공",
               "originallink":"https://www.hankyung.com/article/1",
               "link":"https://n.news.naver.com/1",
               "pubDate":"Sat, 12 Jul 2026 09:00:00 +0900"}]}
            """;
    /** 진짜로 기사가 없다 — 응답은 멀쩡하다 */
    private static final String EMPTY = """
            {"lastBuildDate":"Sat, 31 Aug 2026 19:00:00 +0900","total":0,"items":[]}
            """;
    /** 오류도 200으로 온다 (설계 I137) */
    private static final String ERROR = """
            {"errorMessage":"Invalid search api","errorCode":"SE01"}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FakeCache cache = new FakeCache();
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    @DisplayName("두 번째부터는 네이버를 안 부른다")
    void asksNaverOnlyOnce() {
        final NaverNewsAdapter adapter = adapter(BODY);

        final List<NewsArticle> first = adapter.search("동탄역", 10);
        final List<NewsArticle> second = adapter.search("동탄역", 10);

        assertThat(calls.get()).as("담아 뒀는데 또 부르면 캐시가 아니다").isOne();
        assertThat(second).isEqualTo(first).hasSize(1);
    }

    @Test
    @DisplayName("하루 담아 둔다")
    void keepsItForADay() {
        adapter(BODY).search("동탄역", 10);

        assertThat(cache.ttl).isEqualTo(Duration.ofHours(24));
        assertThat(cache.namespace).isEqualTo(CachePort.NEWS);
    }

    /**
     * 키는 <b>검색어</b>다 (설계 I246).
     *
     * <p>매물 번호가 아닙니다 — 같은 단지의 매물 둘은 검색어가 같고,
     * 그러면 <b>한 번만 물으면 됩니다.</b>
     */
    @Test
    @DisplayName("검색어가 같으면 매물이 달라도 한 번만 부른다")
    void sharesAcrossPropertiesWithTheSameQuery() {
        final NaverNewsAdapter adapter = adapter(BODY);

        adapter.search("성북구 정릉동 한화포레나정릉", 10);
        adapter.search("성북구 정릉동 한화포레나정릉", 10);

        assertThat(calls.get()).isOne();
        assertThat(cache.held.keySet()).containsExactly("성북구 정릉동 한화포레나정릉|10");
    }

    /** 개수를 바꿨는데 <b>옛 개수로 담아 둔 것</b>이 나오면 안 된다 */
    @Test
    @DisplayName("가져올 개수가 다르면 따로 담는다")
    void limitIsPartOfTheKey() {
        final NaverNewsAdapter adapter = adapter(BODY);

        adapter.search("동탄역", 10);
        adapter.search("동탄역", 30);

        assertThat(calls.get()).isEqualTo(2);
    }

    /**
     * <b>이 테스트가 이 기능의 핵심입니다.</b>
     *
     * <p>기사가 없는 단지는 앞으로도 없습니다. 안 담으면 <b>상세를 열 때마다</b>
     * 네이버를 부르는데, 그게 바로 캐시를 넣은 이유입니다 — 정작 가장 필요한
     * 경우에 캐시가 아무 일도 안 하게 됩니다 ([I219]와 같은 처방).
     */
    @Test
    @DisplayName("진짜로 0건이면 그것도 담는다 — 없는 단지는 계속 없다")
    void cachesAGenuineEmptyAnswer() {
        final NaverNewsAdapter adapter = adapter(EMPTY);

        assertThat(adapter.search("없는단지", 10)).isEmpty();
        assertThat(adapter.search("없는단지", 10)).isEmpty();

        assertThat(calls.get()).as("0건이라고 매번 다시 물으면 캐시가 없는 것과 같다").isOne();
    }

    @Test
    @DisplayName("응답이 오류면 담지 않는다 — 잠깐 죽은 것을 하루로 굳히면 안 된다")
    void doesNotCacheAnError() {
        final NaverNewsAdapter adapter = adapter(ERROR);

        adapter.search("동탄역", 10);
        adapter.search("동탄역", 10);

        assertThat(cache.held).as("오류를 담으면 하루 종일 기사 없음이 된다").isEmpty();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("폴백이 돌면 담지 않는다")
    void doesNotCacheAFallback() {
        final NaverNewsAdapter adapter = adapter(null);   // FallbackFactory 가 null 을 준다

        adapter.search("동탄역", 10);
        adapter.search("동탄역", 10);

        assertThat(cache.held).isEmpty();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("본문이 깨져 있으면 담지 않는다")
    void doesNotCacheGarbage() {
        final NaverNewsAdapter adapter = adapter("{ 깨진 JSON");

        adapter.search("동탄역", 10);

        assertThat(cache.held).isEmpty();
    }

    /** Redis 가 죽어도 기사는 나와야 합니다 — 캐시는 거들 뿐입니다 */
    @Test
    @DisplayName("캐시가 죽어도 기사는 나온다")
    void survivesADeadCache() {
        final CachePort broken = mock(CachePort.class);
        given(broken.get(any(), any())).willThrow(new IllegalStateException("Redis down"));

        final NaverNewsAdapter adapter = new NaverNewsAdapter(
                clientReturning(BODY), objectMapper, broken, "id", "secret");

        assertThat(adapter.search("동탄역", 10)).hasSize(1);
    }

    private NaverNewsAdapter adapter(String body) {
        return new NaverNewsAdapter(clientReturning(body), objectMapper, cache, "id", "secret");
    }

    private NaverSearchFeignClient clientReturning(String body) {
        final NaverSearchFeignClient client = mock(NaverSearchFeignClient.class);
        given(client.searchNews(anyString(), anyString(), anyString(), anyInt(), anyString()))
                .willAnswer(invocation -> {
                    calls.incrementAndGet();
                    return body;
                });
        return client;
    }

    /** 실제로 담기는 것을 본다 — mock 으로는 "담았다"만 알고 "무엇이 담겼나"를 못 본다 */
    private static final class FakeCache implements CachePort {

        private final Map<String, String> held = new HashMap<>();
        private String namespace;
        private Duration ttl;

        @Override
        public Optional<String> get(String namespace, String key) {
            return Optional.ofNullable(held.get(key));
        }

        @Override
        public void put(String namespace, String key, String json, Duration ttl) {
            this.namespace = namespace;
            this.ttl = ttl;
            held.put(key, json);
        }

        @Override
        public void evict(String namespace, String key) {
            held.remove(key);
        }

        @Override
        public void evictAll(String namespace) {
            held.clear();
        }
    }
}
