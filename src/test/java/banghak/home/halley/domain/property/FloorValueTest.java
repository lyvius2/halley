package banghak.home.halley.domain.property;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 층은 숫자만이 아니다 (설계 I286).
 *
 * <p>네이버는 저층 매물의 층수를 감춰 `저/15층` 처럼 밴드로 줍니다. 채점은 이미 밴드를
 * 다루는데 들어오는 길이 숫자뿐이라, 밴드로 적힌 매물은 층이 통째로 사라졌습니다.
 */
@DisplayName("층 값 가르기 (설계 I286)")
class FloorValueTest {

    @Test
    @DisplayName("숫자는 층수로 담는다")
    void readsANumber() {
        // when
        final FloorValue value = FloorValue.of("3").orElseThrow();

        // then
        assertThat(value.floorNo()).isEqualTo(3);
        assertThat(value.band()).isNull();
    }

    @Test
    @DisplayName("저·중·고는 밴드로 담는다")
    void readsABand() {
        // then
        assertThat(FloorValue.of("저").orElseThrow().band()).isEqualTo(FloorBand.LOW);
        assertThat(FloorValue.of("중").orElseThrow().band()).isEqualTo(FloorBand.MID);
        assertThat(FloorValue.of("고").orElseThrow().band()).isEqualTo(FloorBand.HIGH);
        assertThat(FloorValue.of("저").orElseThrow().floorNo())
                .as("밴드로 적힌 층에는 층수가 없다")
                .isNull();
    }

    @Test
    @DisplayName("`층` 이 붙어 와도 읽는다 — 예전에 담긴 표기다")
    void toleratesTheFloorSuffix() {
        // then
        assertThat(FloorValue.of("중층").orElseThrow().band()).isEqualTo(FloorBand.MID);
        assertThat(FloorValue.of("3층").orElseThrow().floorNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("알 수 없는 글자는 비운다 — 예외를 던지지 않는다")
    void leavesUnknownTextEmpty() {
        // then
        assertThat(FloorValue.of("옥탑")).isEmpty();
        assertThat(FloorValue.of("")).isEmpty();
        assertThat(FloorValue.of(null)).isEmpty();
        assertThat(FloorValue.isValid("옥탑")).isFalse();
        assertThat(FloorValue.isValid("저")).isTrue();
    }

    @Test
    @DisplayName("화면에 되살릴 글자를 돌려준다")
    void rendersALabel() {
        // then
        assertThat(FloorValue.label(3, null)).isEqualTo("3");
        assertThat(FloorValue.label(null, FloorBand.LOW)).isEqualTo("저");
        assertThat(FloorValue.label(null, null)).isNull();
    }
}
