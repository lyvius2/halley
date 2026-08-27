package banghak.home.halley.ingest.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class NaverListingTextParserTest {

    private final NaverListingTextParser parser = new NaverListingTextParser(NaverListingTextParser.defaultExtractors());

    @Test
    @DisplayName("매매 아파트 실제 붙여넣기 샘플에서 핵심 필드를 파싱한다")
    void parsesSaleFixture() {
        // given
        final String raw = fixture("naver_apt_sale.txt");

        // when
        final ParsedListing parsed = parser.parse(raw);

        // then
        assertThat(parsed.field("name").value()).isEqualTo("독립문삼호");
        assertThat(parsed.field("naverArticleNo").value()).isEqualTo("A12345678");
        assertThat(parsed.field("dealType").value()).isEqualTo("매매");
        assertThat(parsed.field("dongHo").value()).isEqualTo("101동 501호");
        assertThat(parsed.field("priceDeposit").value()).isEqualTo(1_500_000_000L);
        assertThat(parsed.field("kbPrice").value()).isEqualTo(1_350_000_000L);
        assertThat(parsed.field("priceMonthly").confidence()).isEqualTo(Confidence.MISSING);
        assertThat(parsed.field("maintenanceFee").value()).isEqualTo(300_000);
        assertThat(parsed.field("roomBath").value()).isEqualTo("3/2");
        assertThat(parsed.field("heatingType").value()).isEqualTo("지역난방");
        assertThat(parsed.field("subway").value()).isEqualTo("독립문역 7분");
        assertThat(parsed.field("subwayMinutes").value()).isEqualTo(7);
        assertThat(parsed.field("school").value()).isEqualTo("독립문초등학교 5분");
        assertThat(parsed.field("schoolMinutes").value()).isEqualTo(5);
        assertThat(String.valueOf(parsed.field("areaExclusiveM2").value())).isEqualTo("84.98");
        assertThat(parsed.field("floor").value()).isEqualTo("12/24");
        assertThat(parsed.field("approvalYear").value()).isEqualTo(2020);
        assertThat(parsed.field("totalHouseholds").value()).isEqualTo(1208);
        assertThat(String.valueOf(parsed.field("parkingPerHousehold").value())).isEqualTo("1.2");
    }

    @Test
    @DisplayName("전세 아파트 샘플에서 보증금을 파싱하고 초순 입주일을 DERIVED로 추정한다")
    void parsesJeonseFixture() {
        // given
        final String raw = fixture("naver_apt_jeonse.txt");

        // when
        final ParsedListing parsed = parser.parse(raw);

        // then
        assertThat(parsed.field("dealType").value()).isEqualTo("전세");
        assertThat(parsed.field("priceDeposit").value()).isEqualTo(400_000_000L);
        assertThat(parsed.field("moveIn").value()).isEqualTo("즉시입주");
        assertThat(parsed.field("moveIn").confidence()).isEqualTo(Confidence.EXACT);
    }

    @Test
    @DisplayName("월세 오피스텔 샘플에서 보증금/월세 슬래시 표기를 파싱한다")
    void parsesMonthlyOfficetelFixture() {
        // given
        final String raw = fixture("naver_officetel_monthly.txt");

        // when
        final ParsedListing parsed = parser.parse(raw);

        // then
        assertThat(parsed.field("dealType").value()).isEqualTo("월세");
        assertThat(parsed.field("priceDeposit").value()).isEqualTo(50_000_000L);
        assertThat(parsed.field("priceMonthly").value()).isEqualTo(800_000L);
        assertThat(parsed.field("maintenanceFee").value()).isEqualTo(100_000);
    }

    @Test
    @DisplayName("날짜 지정 입주일(초순)은 DERIVED로 2027-01-05를 추정한다")
    void derivesMoveInDate() {
        // given
        final String raw = """
                매매
                매매가
                10억
                입주가능일
                2027년 01월 초순
                매물번호
                C1
                """;

        // when
        final ParsedListing parsed = parser.parse(raw);

        // then
        assertThat(parsed.field("moveIn").confidence()).isEqualTo(Confidence.DERIVED);
        assertThat(parsed.field("moveIn").note()).isEqualTo("2027-01-05로 추정");
    }

    @Test
    @DisplayName("네이버 형식이 아닌 텍스트는 예외 없이 모두 MISSING으로 기록한다")
    void malformedTextRecordsMissingWithoutThrowing() {
        // given
        final String raw = "이것은 네이버 매물 상세가 아닙니다.";

        // when
        final ParsedListing parsed = parser.parse(raw);

        // then
        assertThat(parsed.field("dealType").confidence()).isEqualTo(Confidence.MISSING);
        assertThat(parsed.field("name").confidence()).isEqualTo(Confidence.MISSING);
        assertThat(parsed.field("priceDeposit").confidence()).isEqualTo(Confidence.MISSING);
    }

    private String fixture(String name) {
        try {
            final var resource = getClass().getClassLoader().getResource("fixtures/" + name);
            return new String(Objects.requireNonNull(resource).openStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("fixture 로드 실패: " + name, e);
        }
    }
}
