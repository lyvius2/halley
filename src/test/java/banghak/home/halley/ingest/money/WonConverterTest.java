package banghak.home.halley.ingest.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WonConverterTest {

    @Test
    @DisplayName("억/만/원 조합을 원 단위로 변환한다")
    void toWon() {
        assertThat(WonConverter.toWon("15억")).isEqualTo(1_500_000_000L);
        assertThat(WonConverter.toWon("10억원")).isEqualTo(1_000_000_000L);
        assertThat(WonConverter.toWon("13억 5,000만원")).isEqualTo(1_350_000_000L);
        assertThat(WonConverter.toWon("4,950만원")).isEqualTo(49_500_000L);
        assertThat(WonConverter.toWon("17만 4,081원")).isEqualTo(174_081L);
        assertThat(WonConverter.toWon("80만원")).isEqualTo(800_000L);
    }

    @Test
    @DisplayName("금액 패턴이 없으면 null을 반환한다")
    void noPatternReturnsNull() {
        assertThat(WonConverter.toWon("0")).isNull();
        assertThat(WonConverter.toWon("협의")).isNull();
        assertThat(WonConverter.toWon(null)).isNull();
    }
}
