package banghak.home.halley.ingest.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KB시세를 못 읽던 문제 (설계 I159).
 *
 * <p>텍스트에 <b>분명히 있는데</b> 파싱이 비어 있었습니다. 네이버 화면은 대부분 라벨과 값을
 * 줄로 나누는데 <b>대출 계산기 블록만 붙여 씁니다.</b>
 *
 * <pre>
 * 매매가                    ← 라벨
 * 7억 5,000만원             ← 값 (다음 줄)
 *
 * KB시세 7억 4,000만원       ← 라벨과 값이 한 줄
 * </pre>
 */
@DisplayName("KB시세 파싱 (설계 I159)")
class KbPriceParsingTest {

    private final NaverListingTextParser parser = new NaverListingTextParser(NaverListingTextParser.defaultExtractors());

    @Test
    @DisplayName("라벨과 값이 한 줄이어도 읽는다 — 실제 매물 원문으로 확인한다")
    void readsKbPriceOnTheSameLine() {
        final ParsedListing parsed = parser.parse(fixture());

        assertThat(parsed.field("kbPrice")).isNotNull();
        assertThat(parsed.field("kbPrice").value()).isEqualTo(740_000_000L);
    }

    @Test
    @DisplayName("다음 줄에 있는 값은 그대로 읽는다 — 그쪽이 일반형이다")
    void stillReadsNextLineValues() {
        final ParsedListing parsed = parser.parse(fixture());

        // 매매가는 라벨 다음 줄에 있다
        assertThat(parsed.field("priceDeposit").value()).isEqualTo(750_000_000L);
    }

    @Test
    @DisplayName("KB시세를 매매가로 착각하지 않는다 — 두 값이 다르다")
    void doesNotConfuseKbPriceWithAskingPrice() {
        final ParsedListing parsed = parser.parse(fixture());

        assertThat(parsed.field("priceDeposit").value())
                .isNotEqualTo(parsed.field("kbPrice").value());
    }

    @Test
    @DisplayName("'관리비부과기준'을 '관리비'로 잘못 읽지 않는다 — 라벨 뒤에 글자가 붙으면 다른 라벨이다")
    void doesNotMatchLongerLabels() {
        final ParsedListing parsed = parser.parse(fixture());

        // 관리비는 '21만원상세보기' → 21만원
        assertThat(parsed.field("maintenanceFee").value()).isEqualTo(210_000);
    }

    private String fixture() {
        try {
            return Files.readString(
                    Path.of("src/test/resources/fixtures/naver-sanggye-7.txt"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
