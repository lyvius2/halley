package banghak.home.halley.ingest.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제로 붙여넣은 글에서 면적을 제대로 읽는가 (설계 I233).
 *
 * <p>상계주공7단지의 전용면적에 <b>공급면적(71.02)</b> 이 들어가 있었습니다.
 * 그 때문에 국토부 실거래가 <b>한 건도 안 맞았습니다</b> — 실제 전용은 49.94㎡ 이고
 * 그 평형은 2025년에만 16건 거래됐습니다.
 *
 * <p>파서 탓인지 사람 탓인지 <b>추측하지 않고</b> 진짜 원문으로 돌려 봅니다.
 */
@DisplayName("상계주공7단지 원문 면적 파싱 (설계 I233)")
class SanggyeAreaParseTest {

    /** 사용자가 실제로 붙여넣은 글에서 면적 언저리만 잘라 왔습니다. */
    private static final String RAW = """
            상계주공7단지 714동
            매매 7억 5,000
            3,491만원/3.3㎡평당가 도움말
            알림관심매물
            공유하기
            재건축71㎡ (전용49)고/15층남향
            면적 단위 변경평
            정상입주 남향 재건축통과 복합정비구역지정 노원역세권

            확인매물 2026. 08. 27.
            기본 정보
            매매가
            7억 5,000만원
            관리비부과기준
            정액관리비
            관리비
            21만원상세보기
            다음
            1 번째선택됨
            2 번째
            3 번째
            공급면적
            71.02㎡면적 단위 변경평
            전용면적
            49.94㎡ (전용률 70%)
            해당층/총층
            고/15층
            방수/욕실수
            2/1개
            향
            (안방 기준) 남향
            복층여부
            단층
            입주가능일
            즉시입주 협의 가능
            매물번호
            2646237382
            """;

    private final NaverListingTextParser parser = new NaverListingTextParser(NaverListingTextParser.defaultExtractors());

    @Test
    @DisplayName("공급 71.02 · 전용 49.94 를 각자 제 자리에 넣는다")
    void readsBothAreas() {
        final ParsedListing parsed = parser.parse(RAW);

        assertThat(area(parsed, "areaSupplyM2")).isEqualByComparingTo(new BigDecimal("71.02"));
        assertThat(area(parsed, "areaExclusiveM2")).isEqualByComparingTo(new BigDecimal("49.94"));
    }

    /**
     * <b>전용이 공급보다 클 수는 없습니다.</b> 뒤바뀌면 국토부 실거래가 영영 안 맞는데,
     * 화면에는 "거래 내역이 없습니다" 로만 보입니다.
     */
    @Test
    @DisplayName("전용면적이 공급면적보다 작다")
    void exclusiveIsSmallerThanSupply() {
        final ParsedListing parsed = parser.parse(RAW);

        assertThat(area(parsed, "areaExclusiveM2"))
                .isLessThan(area(parsed, "areaSupplyM2"));
    }

    /**
     * 요약 줄에도 숫자가 있습니다 — `재건축71㎡ (전용49)고/15층남향`.
     * <b>거기서 집으면 49 로 잘려</b> 실제 49.94 와 다릅니다.
     */
    @Test
    @DisplayName("요약 줄의 대략치가 아니라 상세표의 값을 읽는다")
    void prefersTheDetailTableOverTheSummaryLine() {
        final ParsedListing parsed = parser.parse(RAW);

        assertThat(area(parsed, "areaExclusiveM2")).isNotEqualByComparingTo(new BigDecimal("49"));
    }

    private BigDecimal area(ParsedListing parsed, String key) {
        final Object value = parsed.fields().get(key).value();
        assertThat(value).as("%s 를 못 읽었다", key).isNotNull();
        return (BigDecimal) value;
    }
}
