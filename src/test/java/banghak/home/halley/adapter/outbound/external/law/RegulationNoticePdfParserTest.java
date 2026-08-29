package banghak.home.halley.adapter.outbound.external.law;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("규제지역 고시 PDF 현황표 파싱 (설계 I73)")
class RegulationNoticePdfParserTest {

    private final RegulationNoticePdfParser parser = new RegulationNoticePdfParser();

    @Test
    @DisplayName("투기과열지구 고시에서 서울 25개구·경기 15곳을 읽는다")
    void parsesSpeculationNotice() throws IOException {
        // when — 국토교통부공고 제2026-883호 실물
        final List<String> areas = parser.parseAreaNames(fixture("speculation-notice-2026-883.pdf"));

        // then
        assertThat(areas).hasSize(40);
        assertThat(areas).contains("서울 강남구", "서울 도봉구", "경기 과천", "경기 의왕");
        // <신규 지정> 3곳도 현황에 합쳐진다 — 이미 지정된 상태다
        assertThat(areas).contains("경기 화성동탄", "경기 용인기흥", "경기 구리");
    }

    @Test
    @DisplayName("조정대상지역 고시도 같은 서식이라 같은 파서로 읽는다")
    void parsesAdjustmentNotice() throws IOException {
        // when — 국토교통부공고 제2026-882호 실물
        final List<String> areas = parser.parseAreaNames(fixture("adjustment-notice-2026-882.pdf"));

        // then
        assertThat(areas).hasSize(40);
        assertThat(areas).contains("서울 강남구", "경기 화성동탄");
    }

    @Test
    @DisplayName("추출 텍스트가 깨져도 숫자·부호가 지역명으로 섞이지 않는다")
    void dropsPunctuationAndDates() throws IOException {
        // given — 원문에서 쉼표가 줄 끝으로 밀리고 `('26.7.1.)` 같은 표기가 섞여 있다
        final List<String> areas = parser.parseAreaNames(fixture("speculation-notice-2026-883.pdf"));

        // then — 시도 접두어를 뗀 이름에 숫자·부호가 없어야 한다
        assertThat(areas).allSatisfy(area ->
                assertThat(area.split(" ")[1]).matches("[가-힣]+"));
    }

    @Test
    @DisplayName("PDF가 비었거나 못 읽으면 빈 목록을 준다 — 예외로 배치를 죽이지 않는다")
    void returnsEmptyOnUnreadableInput() {
        assertThat(parser.parseAreaNames(null)).isEmpty();
        assertThat(parser.parseAreaNames(new byte[0])).isEmpty();
        assertThat(parser.parseAreaNames("not a pdf".getBytes())).isEmpty();
    }

    private byte[] fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/law/" + name)) {
            return in.readAllBytes();
        }
    }
}
