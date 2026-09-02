package banghak.home.halley.adapter.outbound.external.naver;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.news.NewsArticle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("네이버 뉴스 어댑터 (설계 I137)")
class NaverNewsAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 실제 응답 모양 — 검색어가 <b> 로 감싸여 온다. */
    private static final String BODY = """
            {"lastBuildDate":"Sat, 31 Aug 2026 19:00:00 +0900","total":128,"start":1,"display":2,
             "items":[
              {"title":"<b>동탄역</b> 복합환승센터 착공 &quot;2029년 준공&quot;",
               "originallink":"https://www.hankyung.com/article/2026071234",
               "link":"https://n.news.naver.com/mnews/article/015/0001234567",
               "description":"<b>동탄역</b> 일대 …","pubDate":"Sat, 12 Jul 2026 09:00:00 +0900"},
              {"title":"청계동 정비구역 지정 고시",
               "originallink":"https://www.yna.co.kr/view/AKR20260503",
               "link":"https://n.news.naver.com/mnews/article/001/0009876543",
               "description":"…","pubDate":"Sun, 03 May 2026 14:30:00 +0900"}]}
            """;

    /** 늘 비어 있는 캐시 — 담아 두기가 이 테스트들이 보는 것을 가리지 않게 한다 */
    private CachePort missingCache() {
        final CachePort cache = mock(CachePort.class);
        given(cache.get(any(), any())).willReturn(Optional.empty());
        return cache;
    }

    private NaverNewsAdapter adapter(String id, String secret) {
        return new NaverNewsAdapter(mock(NaverSearchFeignClient.class), objectMapper,
                missingCache(), id, secret);
    }

    @Test
    @DisplayName("HTML 태그와 엔티티를 걷어 낸다 — 그대로 넣으면 HTML 주입 통로가 된다")
    void stripsHtml() {
        final List<NewsArticle> articles = adapter("id", "secret").parse(BODY, "동탄역");

        assertThat(articles.getFirst().title())
                .isEqualTo("동탄역 복합환승센터 착공 \"2029년 준공\"");
        assertThat(articles.getFirst().title()).doesNotContain("<b>").doesNotContain("&quot;");
    }

    @Test
    @DisplayName("원 언론사 도메인을 출처로 쓴다 — 네이버 뉴스 링크가 아니라")
    void usesOriginalPublisher() {
        final List<NewsArticle> articles = adapter("id", "secret").parse(BODY, "동탄역");

        assertThat(articles.getFirst().source()).isEqualTo("hankyung.com");
        assertThat(articles.getFirst().link()).startsWith("https://www.hankyung.com");
        assertThat(articles.get(1).source()).isEqualTo("yna.co.kr");
    }

    @Test
    @DisplayName("요일이 어긋나도 날짜를 읽는다 — 요일은 중복 정보라 파싱에 넣지 않는다")
    void parsesPubDate() {
        // 아래 데이터의 요일(Sat/Sun)은 실제 요일과 다르다. 그래도 날짜는 나와야 한다
        final List<NewsArticle> articles = adapter("id", "secret").parse(BODY, "동탄역");

        assertThat(articles.getFirst().publishedOn()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(articles.get(1).publishedOn()).isEqualTo(LocalDate.of(2026, 5, 3));
    }

    @Test
    @DisplayName("오류도 200으로 올 수 있다 — 본문을 봐야 안다")
    void handlesErrorBody() {
        final String error = """
                {"errorMessage":"Not Exist Client ID","errorCode":"024"}""";

        assertThat(adapter("id", "secret").parse(error, "동탄역")).isEmpty();
    }

    @Test
    @DisplayName("키가 없으면 부르지 않는다")
    void skipsWithoutKeys() {
        final NaverSearchFeignClient boom = (a, b, c, d, e) -> {
            throw new AssertionError("불리면 안 된다");
        };
        assertThat(new NaverNewsAdapter(boom, objectMapper, missingCache(), "", "s").search("동탄", 10)).isEmpty();
        assertThat(new NaverNewsAdapter(boom, objectMapper, missingCache(), "i", "").search("동탄", 10)).isEmpty();
        assertThat(new NaverNewsAdapter(boom, objectMapper, missingCache(), "i", "s").search("  ", 10)).isEmpty();
    }

    @Test
    @DisplayName("최신순으로 부른다 — 개발 호재는 정확도순이면 오래된 기사가 위로 온다")
    void searchesByDate() {
        final String[] sort = new String[1];
        final NaverSearchFeignClient client = (id, secret, query, display, s) -> {
            sort[0] = s;
            return BODY;
        };

        new NaverNewsAdapter(client, objectMapper, missingCache(), "i", "s").search("동탄역", 10);

        assertThat(sort[0]).isEqualTo("date");
    }
}
