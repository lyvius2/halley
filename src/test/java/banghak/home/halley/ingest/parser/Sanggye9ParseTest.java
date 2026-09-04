package banghak.home.halley.ingest.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 들여쓰기 없이, 라벨이 <b>따로 줄</b>로 오는 붙여넣기 (설계 I283 후속).
 *
 * <p>같은 화면인데 붙여넣기 모양이 다릅니다 — 여기서는 역 이름에 <b>노선이 공백 없이</b>
 * 붙어 옵니다(`마들역7호선`). 앞선 표본([I283])만으로는 이 모양을 못 잡았습니다.
 */
class Sanggye9ParseTest {

    private final NaverListingTextParser parser =
            new NaverListingTextParser(NaverListingTextParser.defaultExtractors());

    @Test
    @DisplayName("역 이름에 노선이 붙어 와도 역만 가려낸다 (설계 I283)")
    void picksStationNamesGluedToLineNames() {
        // given
        final ParsedListing parsed = parser.parse(fixture());

        // then — `마들역7호선` · `노원역4호선7호선`
        assertThat(parsed.field("subway").value()).isEqualTo("마들역/노원역");
        assertThat(parsed.field("subwayMinutes").value()).isEqualTo(9);
    }

    @Test
    @DisplayName("모양이 달라도 나머지 항목은 그대로 나온다")
    void parsesTheRestOfThisLayout() {
        // given
        final ParsedListing parsed = parser.parse(fixture());

        // then
        assertThat(parsed.field("name").value()).isEqualTo("상계주공9단지");
        assertThat(parsed.field("dongHo").value()).isEqualTo("904동");
        assertThat(parsed.field("dealType").value()).isEqualTo("매매");
        assertThat(parsed.field("priceDeposit").value()).isEqualTo(720_000_000L);
        assertThat(parsed.field("kbPrice").value()).isEqualTo(675_000_000L);
        assertThat(parsed.field("floor").value()).isEqualTo("3/12");
        assertThat(parsed.field("brokerageFee").value()).isEqualTo(2_880_000L);
        assertThat(parsed.field("school").value()).isEqualTo("서울상곡초등학교");
        assertThat(parsed.field("schoolMinutes").value()).isEqualTo(1);
        assertThat(parsed.field("addressJibun").value()).isEqualTo("서울시 노원구 상계동 670");
        assertThat(parsed.field("approvalYear").value()).isEqualTo(1988);
        assertThat(parsed.field("totalHouseholds").value()).isEqualTo(2830);
    }

    private String fixture() {
        final var resource = getClass().getClassLoader()
                .getResource("fixtures/naver-sanggye-9-904.txt");
        assumeTrue(resource != null,
                "픽스처 없음 - 개인정보가 있어 저장소에 두지 않습니다 (설계 I83).");
        try {
            return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("fixture 로드 실패", e);
        }
    }
}
