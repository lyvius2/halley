package banghak.home.halley.adapter.outbound.external.ministry;

import banghak.home.halley.domain.property.ReferenceTrade;
import org.junit.jupiter.api.DisplayName;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 국토부에 <b>몇 건을 달라고 하는가</b> (설계 I219).
 *
 * <p>`numOfRows` 를 안 주면 국토부는 <b>10건만</b> 줍니다. 실측으로 서울 한 구의
 * 한 달 아파트 매매는 200~700건입니다 — 성북구 2025년 6월이 <b>660건</b>이었습니다.
 *
 * <p>우리가 하는 일은 <b>특정 단지를 찾는 것</b>이라, 앞에서 10건을 자르면
 * 거의 못 찾습니다. 실제로 매물 하나가 12개월을 훑고도 <b>0건</b>이었는데,
 * 전체를 받아 보니 그 단지의 거래가 <b>있었습니다.</b>
 */
@DisplayName("국토부 페이지 크기 (설계 I219)")
class MinistryPageSizeTest {

    private static final String EMPTY_XML = """
            <response><body><items></items><totalCount>0</totalCount></body></response>
            """;

    /**
     * 실측 최대가 <b>952건</b>(송파 2025-03)이라 1000은 여유가 48건뿐이었습니다.
     */
    @Test
    @DisplayName("매매 조회에 numOfRows 를 실어 보낸다 — 서울 한 구의 한 달이 최대 950건대다")
    void tradeAsksForManyRows() {
        final AtomicInteger asked = new AtomicInteger();
        final MinistryReferenceAdapter adapter = adapter(asked);

        adapter.fetchTrades("11290", "202506");

        assertThat(asked.get()).isGreaterThan(1000);
    }

    @Test
    @DisplayName("전월세 조회도 마찬가지다")
    void rentAsksForManyRows() {
        final AtomicInteger asked = new AtomicInteger();
        final MinistryReferenceAdapter adapter = adapter(asked);

        adapter.fetchJeonseDeposits("11290", "202506");

        assertThat(asked.get()).isGreaterThanOrEqualTo(1000);
    }

    /**
     * 국토부는 <b>넘쳐도 아무 말 없이</b> 앞에서 잘라 줍니다 (설계 I229).
     * 그러면 화면에는 "거래가 없다"로 나타납니다 — 없는 것과 못 받은 것을
     * 구분할 수 없습니다. 이걸 안 봐서 10건만 받던 것을 오래 몰랐습니다.
     */
    @Test
    @DisplayName("한 페이지에 다 못 담았으면 로그로 알린다 — 조용히 잘리면 '거래 없음'으로 보인다")
    void warnsWhenTruncated() {
        final ListAppender<ILoggingEvent> log = attachAppender();
        final MinistryReferenceAdapter adapter = new MinistryReferenceAdapter(new MinistryReferenceFeignClient() {
            @Override
            public String fetchTrade(String serviceKey, String lawdCd, String dealYmd, int numOfRows) {
                // 총 5,000건이라는데 우리는 한 건만 받았다
                return """
                        <response><body><items><item>
                          <aptNm>가나아파트</aptNm><dealAmount>80,000</dealAmount>
                          <excluUseAr>84.9</excluUseAr><floor>5</floor>
                          <dealYear>2025</dealYear><dealMonth>6</dealMonth><dealDay>1</dealDay>
                        </item></items><totalCount>5000</totalCount></body></response>
                        """;
            }

            @Override
            public String fetchRent(String serviceKey, String lawdCd, String dealYmd, int numOfRows) {
                return EMPTY_XML;
            }
        }, new banghak.home.halley.config.RateGate("test", 0), "key");

        adapter.fetchTrades("11710", "202503");

        assertThat(log.list).anySatisfy(event ->
                assertThat(event.getFormattedMessage())
                        .contains("truncated").contains("totalCount=5000").contains("received=1"));
    }

    @Test
    @DisplayName("다 받았으면 조용하다 — 멀쩡한 응답에 경고를 달면 진짜 경고가 묻힌다")
    void quietWhenComplete() {
        final ListAppender<ILoggingEvent> log = attachAppender();

        adapter(new AtomicInteger()).fetchTrades("11290", "202506");

        assertThat(log.list).noneSatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("truncated"));
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        final Logger logger = (Logger) LoggerFactory.getLogger(MinistryReferenceAdapter.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private MinistryReferenceAdapter adapter(AtomicInteger asked) {
        return new MinistryReferenceAdapter(new MinistryReferenceFeignClient() {
            @Override
            public String fetchTrade(String serviceKey, String lawdCd, String dealYmd, int numOfRows) {
                asked.set(numOfRows);
                return EMPTY_XML;
            }

            @Override
            public String fetchRent(String serviceKey, String lawdCd, String dealYmd, int numOfRows) {
                asked.set(numOfRows);
                return EMPTY_XML;
            }
        }, new banghak.home.halley.config.RateGate("test", 0), "key");
    }

    /** 파서가 살아 있는지도 함께 본다 — 빈 응답을 오류로 오해하면 안 된다. */
    @Test
    @DisplayName("거래가 없는 달은 빈 목록이다 — 조회 실패(null)와 다르다")
    void emptyMonthIsNotAFailure() {
        final List<ReferenceTrade> trades = adapter(new AtomicInteger()).fetchTrades("11290", "202506");

        assertThat(trades).isNotNull().isEmpty();
    }
}
