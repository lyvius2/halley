package banghak.home.halley.domain.property;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지번주소에서 법정동과 번지를 뽑는다 (설계 I257).
 *
 * <p>주소는 <b>실제로 저장된 것</b>을 씁니다. 지어낸 모양으로 시험하면
 * 통과해도 소용없습니다.
 */
@DisplayName("지번주소 (설계 I257)")
class JibunAddressTest {

    @Nested
    @DisplayName("실제로 저장된 주소")
    class Real {

        @ParameterizedTest(name = "{0} → {1} {2}-{3}")
        @CsvSource({
                "'경기도 하남시 망월동 938',        망월동,      938, 0",
                "'서울시 노원구 상계동 692',        상계동,      692, 0",
                "'서울시 성북구 석관동 407',        석관동,      407, 0",
                "'서울시 성북구 안암동3가 138',     안암동3가,   138, 0",
                "'서울시 노원구 공릉동 81',         공릉동,      81,  0",
                "'서울시 성북구 정릉동 1037',       정릉동,      1037, 0",
                "'서울시 성북구 동소문동4가 279',   동소문동4가, 279, 0"
        })
        @DisplayName("동과 번지를 뽑는다")
        void parses(String address, String dong, int bonbun, int bubun) {
            assertThat(JibunAddress.of(address)).get()
                    .isEqualTo(new JibunAddress(dong, bonbun, bubun));
        }
    }

    /**
     * <b>`안암동3가` 가 `안암동` 이 되면 안 됩니다.</b> 국토부는 `안암동3가` 로 주므로
     * 그렇게 끊으면 영영 안 맞습니다 — 정규식으로 {@code 동|읍|면} 에서 끊던 것이
     * 그랬습니다.
     */
    @Test
    @DisplayName("동 이름의 '3가'를 자르지 않는다")
    void keepsTheGaSuffix() {
        assertThat(JibunAddress.of("서울시 성북구 안암동3가 138"))
                .get().extracting(JibunAddress::legalDong).isEqualTo("안암동3가");
    }

    @Test
    @DisplayName("시가 둘이면 뒤엣것이 기준이다 — 서울시 노원구")
    void takesTheLastSigungu() {
        assertThat(JibunAddress.of("서울시 노원구 상계동 692"))
                .get().extracting(JibunAddress::legalDong).isEqualTo("상계동");
    }

    @Test
    @DisplayName("부번이 있으면 갈라 담는다")
    void splitsTheSubLot() {
        assertThat(JibunAddress.of("서울시 성북구 정릉동 372-1")).get()
                .isEqualTo(new JibunAddress("정릉동", 372, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "서울시 성북구 정릉동",              // 번지가 없다
            "서울시 성북구",                     // 동도 없다
            "정릉동 1037",                       // 시·군·구가 없다
            "서울시 성북구 정릉동 삼성래미안",    // 번지 자리가 숫자가 아니다
            ""
    })
    @DisplayName("반쪽이면 비어 있다 — 반쪽으로는 가릴 수 없다")
    void needsBothParts(String address) {
        assertThat(JibunAddress.of(address)).isEmpty();
    }

    @Test
    @DisplayName("null 도 비어 있다")
    void handlesNull() {
        assertThat(JibunAddress.of((String) null)).isEmpty();
    }

    @Nested
    @DisplayName("국토부 응답에서")
    class FromMinistry {

        @Test
        @DisplayName("umdNm 과 jibun 을 그대로 읽는다")
        void readsMinistryFields() {
            assertThat(JibunAddress.of("정릉동", "1037")).get()
                    .isEqualTo(new JibunAddress("정릉동", 1037, 0));
            assertThat(JibunAddress.of("안암동3가", "138-2")).get()
                    .isEqualTo(new JibunAddress("안암동3가", 138, 2));
        }

        @Test
        @DisplayName("번지가 없으면 비어 있다")
        void needsAJibun() {
            assertThat(JibunAddress.of("정릉동", null)).isEmpty();
            assertThat(JibunAddress.of("정릉동", "")).isEmpty();
        }
    }

    @Nested
    @DisplayName("같은 자리인가")
    class SameLot {

        private final JibunAddress mine = new JibunAddress("정릉동", 1037, 0);

        @Test
        @DisplayName("동과 번지가 같으면 같은 자리다 — 이름이 달라도")
        void sameDongAndLot() {
            assertThat(mine.sameLot(new JibunAddress("정릉동", 1037, 0))).isTrue();
        }

        @Test
        @DisplayName("번지가 다르면 같은 자리가 아니다")
        void differentLot() {
            assertThat(mine.sameLot(new JibunAddress("정릉동", 1038, 0))).isFalse();
            assertThat(mine.sameLot(new JibunAddress("정릉동", 1037, 1))).isFalse();
        }

        @Test
        @DisplayName("동이 다르면 같은 자리가 아니다")
        void differentDong() {
            assertThat(mine.sameLot(new JibunAddress("석관동", 1037, 0))).isFalse();
            assertThat(mine.sameDong(new JibunAddress("석관동", 1037, 0))).isFalse();
        }

        /** <b>동만 같은 것은 같다고 하지 않습니다.</b> 한 동에 단지가 여럿입니다 */
        @Test
        @DisplayName("동만 같은 것은 같은 자리가 아니다")
        void dongAloneIsNotEnough() {
            final JibunAddress other = new JibunAddress("정릉동", 500, 0);

            assertThat(mine.sameDong(other)).isTrue();
            assertThat(mine.sameLot(other)).isFalse();
        }
    }
}
