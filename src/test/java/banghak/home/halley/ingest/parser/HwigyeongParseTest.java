package banghak.home.halley.ingest.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 제목 한 줄에 <b>전부 붙어 오는</b> 붙여넣기 (설계 I283).
 *
 * <pre>
 * 휘경롯데 102동매매 8억 8,0003,481만원/3.3㎡평당가 도움말
 * </pre>
 *
 * <p>단지명·동/호·거래유형이 <b>공백 없이</b> 이어지고, 섹션 라벨에도 UI 문구가
 * 붙어 옵니다(`배정 초등학교상세내용 숨기기`). 실제로 이 붙여넣기에서 일곱 항목이
 * 통째로 빠졌습니다.
 */
class HwigyeongParseTest {

    private final NaverListingTextParser parser =
            new NaverListingTextParser(NaverListingTextParser.defaultExtractors());

    @Test
    @DisplayName("제목·라벨이 붙어 와도 핵심 항목을 가려낸다 (설계 I283)")
    void parsesGluedTitleAndLabels() {
        // given
        final ParsedListing parsed = parser.parse(fixture());

        // then — 제목 한 줄에서 단지명과 동을 가른다
        assertThat(parsed.field("name").value()).isEqualTo("휘경롯데");
        assertThat(parsed.field("dongHo").value()).isEqualTo("102동");

        // 거래유형은 제목이 말한다 — 아래쪽 "매매/전세" 토글을 읽으면 안 된다
        assertThat(parsed.field("dealType").value()).isEqualTo("매매");
        assertThat(parsed.field("priceDeposit").value()).isEqualTo(880_000_000L);

        // 라벨과 값이 한 줄에 붙고 뒤에 군더더기까지 붙은 경우
        assertThat(parsed.field("kbPrice").value()).isEqualTo(730_000_000L);

        // 층은 숫자가 아니라 밴드(저/중/고)로 오기도 한다
        assertThat(parsed.field("floor").value()).isEqualTo("고/19");

        assertThat(parsed.field("brokerageFee").value()).isEqualTo(3_520_000L);

        // 라벨에 UI 문구가 붙어 있어(`배정 초등학교상세내용 숨기기`) 통째로 빠졌던 것들
        assertThat(parsed.field("school").value()).isEqualTo("서울휘경초등학교");
        assertThat(parsed.field("schoolMinutes").value()).isEqualTo(19);

        // 라벨 다음이 빈 줄이라 빠졌던 것
        assertThat(parsed.field("subway").value()).isEqualTo("회기역/외대앞역");
        assertThat(parsed.field("subwayMinutes").value()).isEqualTo(9);
    }

    @Test
    @DisplayName("이미 되던 항목은 그대로 나온다 — 고치다 흘리지 않는다")
    void keepsWhatAlreadyWorked() {
        // given
        final ParsedListing parsed = parser.parse(fixture());

        // then
        assertThat(parsed.field("naverArticleNo").value()).isEqualTo("2646114365");
        assertThat(String.valueOf(parsed.field("areaSupplyM2").value())).isEqualTo("83.56");
        assertThat(String.valueOf(parsed.field("areaExclusiveM2").value())).isEqualTo("59.94");
        assertThat(parsed.field("roomBath").value()).isEqualTo("3/1");
        assertThat(parsed.field("direction").value()).isEqualTo("동향");
        assertThat(parsed.field("heatingType").value()).isEqualTo("개별난방 / 도시가스");
        assertThat(parsed.field("addressJibun").value()).isEqualTo("서울시 동대문구 휘경동 78");
        assertThat(parsed.field("approvalYear").value()).isEqualTo(2000);
        assertThat(parsed.field("totalHouseholds").value()).isEqualTo(265);
        assertThat(String.valueOf(parsed.field("parkingPerHousehold").value())).isEqualTo("1.07");
        assertThat(parsed.field("agentName").value()).isEqualTo("장수호");
        assertThat(parsed.field("agentOfficeName").value()).isEqualTo("이대째백년부동산공인중개사사무소");
        assertThat(String.valueOf(parsed.field("brokerageRate").value())).isEqualTo("0.4");
    }

    private String fixture() {
        final var resource = getClass().getClassLoader()
                .getResource("fixtures/naver-hwigyeong-lotte-102.txt");
        assumeTrue(resource != null,
                "픽스처 없음 - 개인정보가 있어 저장소에 두지 않습니다 (설계 I83).");
        try {
            return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("fixture 로드 실패", e);
        }
    }
}
