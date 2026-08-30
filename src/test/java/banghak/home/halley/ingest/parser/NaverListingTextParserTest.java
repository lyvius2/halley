package banghak.home.halley.ingest.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        assertThat(parsed.field("maintenanceFee").value()).isEqualTo(300_000);
        assertThat(parsed.field("roomBath").value()).isEqualTo("3/2");
        assertThat(parsed.field("heatingType").value()).isEqualTo("지역난방");
        assertThat(parsed.field("subway").value()).isEqualTo("독립문역 7분");
        assertThat(parsed.field("subwayMinutes").value()).isEqualTo(7);
        assertThat(parsed.field("school").value()).isEqualTo("독립문초등학교");
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
    @DisplayName("실제 네이버 붙여넣기 샘플에서 단지명·동호·매매가·관리비·도보시간 등을 파싱한다")
    void parsesRealNaverSample() {
        // given
        final String raw = fixture("naver_apt_real.txt");

        // when
        final ParsedListing parsed = parser.parse(raw);

        // then
        assertThat(parsed.field("name").value()).isEqualTo("래미안석관");
        assertThat(parsed.field("dongHo").value()).isEqualTo("113동");
        assertThat(parsed.field("dealType").value()).isEqualTo("매매");
        assertThat(parsed.field("priceDeposit").value()).isEqualTo(1_000_000_000L);
        // 상단 요약(15만원)이 아니라 하단 상세의 '월 평균 26만 6,408원'을 쓴다 — 설계 I53
        assertThat(parsed.field("maintenanceFee").value()).isEqualTo(266_408);
        // 실물은 `3/2개`로 오지만 저장은 `3/2`로 통일한다 (설계 I82)
        assertThat(parsed.field("roomBath").value()).isEqualTo("3/2");
        assertThat(parsed.field("floor").value()).isEqualTo("1/12");
        assertThat(parsed.field("addressJibun").value()).isEqualTo("서울시 성북구 석관동 407");
        assertThat(parsed.field("approvalYear").value()).isEqualTo(2009);
        assertThat(parsed.field("totalHouseholds").value()).isEqualTo(580);
        assertThat(String.valueOf(parsed.field("parkingPerHousehold").value())).isEqualTo("1.17");
        assertThat(parsed.field("subwayMinutes").value()).isEqualTo(5);
        assertThat(parsed.field("school").value()).isEqualTo("서울석관초등학교");
        assertThat(parsed.field("schoolMinutes").value()).isEqualTo(7);
        assertThat(parsed.field("naverArticleNo").value()).isEqualTo("2645869065");
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
        assertThat(parsed.field("priceDeposit").confidence()).isEqualTo(Confidence.MISSING);
    }

    @Test
    @DisplayName("중개사·중개보수·세금 블록을 파싱한다")
    void parsesAgentBrokerageAndTax() {
        // given
        final String raw = fixture("naver_apt_sale_agent.txt");

        // when
        final ParsedListing parsed = parser.parse(raw);

        // then — 중개사
        assertThat(parsed.field("agentName").value()).isEqualTo("우성정");
        assertThat(parsed.field("agentOfficeName").value()).isEqualTo("혜화공인중개사사무소");
        // 유선·휴대폰이 줄바꿈 없이 붙어 온다: `02-764-4222010-7407-4222`
        assertThat(parsed.field("agentPhone").value()).isEqualTo("02-764-4222");
        assertThat(parsed.field("agentMobile").value()).isEqualTo("010-7407-4222");
        assertThat(parsed.field("agentAddress").value()).isEqualTo("서울특별시 종로구 명륜2가 4 상가1층 6호");
        assertThat(parsed.field("agentRegistrationNo").value()).isEqualTo("11110202200028");

        // then — 중개보수·세금
        assertThat(parsed.field("brokerageFee").value()).isEqualTo(5_600_000L);
        assertThat(String.valueOf(parsed.field("brokerageRate").value())).isEqualTo("0.5");
        assertThat(parsed.field("acquisitionTax").value()).isEqualTo(36_960_000L);
        assertThat(parsed.field("propertyTax").value()).isEqualTo(1_050_000L);
        assertThat(parsed.field("comprehensiveTax").value()).isEqualTo("과세대상 아님");
    }

    @Test
    @DisplayName("관리비는 상단 요약이 아니라 상세의 '월 평균'을 쓴다")
    void prefersDetailedMaintenanceFee() {
        // given — 상단에는 '18만원', 상세에는 '월 평균 23만 4,762원'이 있다
        final String raw = fixture("naver_apt_sale_agent.txt");

        // when
        final ParsedListing parsed = parser.parse(raw);

        // then
        assertThat(parsed.field("maintenanceFee").value()).isEqualTo(234_762);
        assertThat(parsed.field("maintenanceFee").confidence()).isEqualTo(Confidence.DERIVED);
    }

    @Test
    @DisplayName("위치 라벨은 단지·중개사 양쪽에 있어 구간을 나눠 읽는다")
    void resolvesDuplicateLocationLabel() {
        // given
        final String raw = fixture("naver_apt_sale_agent.txt");

        // when
        final ParsedListing parsed = parser.parse(raw);

        // then — 단지 주소와 중개사 주소가 서로 섞이지 않는다
        assertThat(parsed.field("addressJibun").value()).isEqualTo("서울시 종로구 명륜2가 4");
        assertThat(parsed.field("agentAddress").value()).isEqualTo("서울특별시 종로구 명륜2가 4 상가1층 6호");
    }

    @Test
    @DisplayName("전세 실물에서 전세가를 읽는다 — `보증금`만 보던 탓에 값이 통째로 비었다")
    void parsesRealJeonseFixture() {
        // given — 미사강변트래지안 실제 붙여넣기
        final ParsedListing parsed = parser.parse(fixture("naver_apt_jeonse_real.txt"));

        // then
        assertThat(parsed.field("dealType").value()).isEqualTo("전세");
        assertThat(parsed.field("priceDeposit").value()).isEqualTo(650_000_000L);
        assertThat(parsed.field("name").value()).isEqualTo("미사강변트래지안");
        assertThat(parsed.field("maintenanceFee").value()).isEqualTo(250_000);
        assertThat(String.valueOf(parsed.field("areaExclusiveM2").value())).isEqualTo("59.3");
        assertThat(parsed.field("addressJibun").value()).isEqualTo("경기도 하남시 망월동 938");
    }

    @Test
    @DisplayName("표기가 흔들리는 값을 다듬는다 — 같은 값이 두 모양으로 저장되면 안 된다")
    void cleansNoisyValues() {
        // given — 실물은 `3/2개`, `(거실 기준) 남동향`으로 온다
        final ParsedListing parsed = parser.parse(fixture("naver_apt_jeonse_real.txt"));

        // then
        assertThat(parsed.field("roomBath").value()).isEqualTo("3/2");
        assertThat(parsed.field("direction").value()).isEqualTo("남동향");
    }

    @Test
    @DisplayName("중개사 이름과 사무소명을 사무소명 기준으로 가른다 — 줄 순서만 믿으면 밀린다")
    void anchorsAgentBlockOnOfficeName() {
        // given — `중개소` 아래에 `중개사 프로필 이미지`라는 이미지 대체텍스트가 한 줄 더 있다.
        // 순서대로 읽으면 이름 자리에 그 문구가, 사무소명 자리에 사람 이름이 들어간다
        final ParsedListing parsed = parser.parse(fixture("naver_apt_jeonse_real.txt"));

        // then
        assertThat(parsed.field("agentName").value()).isEqualTo("김덕림");
        assertThat(parsed.field("agentOfficeName").value()).isEqualTo("미사강변휴플러스공인중개사사무소");
        assertThat(parsed.field("agentRegistrationNo").value()).isEqualTo("41450-2025-00088");
    }

    /**
     * 픽스처는 <b>저장소에 없습니다</b> (설계 I83). 실제 매물 페이지를 그대로 담고 있어
     * 공인중개사 성함·휴대폰 번호 같은 개인정보가 들어갑니다.
     *
     * <p>없으면 <b>실패가 아니라 건너뜁니다.</b> 파일이 없다는 이유로 빨간 실패가 뜨면
     * 진짜 회귀와 구분되지 않습니다 — 원인 모를 실패는 결국 무시하게 됩니다.
     */
    private String fixture(String name) {
        final var resource = getClass().getClassLoader().getResource("fixtures/" + name);
        assumeTrue(resource != null,
                "픽스처 " + name + " 없음 - 개인정보가 있어 저장소에 두지 않습니다 (설계 I83). "
                        + "src/test/resources/fixtures/ 에 실제 붙여넣기를 두면 검증됩니다.");
        try {
            return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("fixture 로드 실패: " + name, e);
        }
    }
}
