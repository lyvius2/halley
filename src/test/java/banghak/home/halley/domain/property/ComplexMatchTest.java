package banghak.home.halley.domain.property;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 실거래가 이 매물의 것인가 (설계 I257).
 *
 * <p>규칙이 <b>여기 하나</b>입니다. [I230]에서 같은 규칙이 두 벌이라
 * 전망이 늘 자료 부족이었습니다.
 */
@DisplayName("단지 매칭 (설계 I257)")
class ComplexMatchTest {

    private static final String MY_ADDRESS = "서울시 성북구 정릉동 1037";
    private static final String MY_NAME = "한화포레나정릉";

    /**
     * <b>이것이 이 작업의 이유입니다.</b>
     *
     * <p>대우 `푸르지오` → 한화 `꿈에그린` → `포레나` 로 이어진 단지라
     * 국토부에 옛 이름으로 남아 있으면 <b>글자가 하나도 안 겹칩니다.</b>
     */
    @Test
    @DisplayName("이름이 통째로 달라도 같은 자리면 같은 단지다")
    void matchesByLotWhenTheNameChanged() {
        final ReferenceTrade renamed = trade("정릉꿈에그린", "정릉동", "1037");

        assertThat(ComplexMatch.same(MY_ADDRESS, MY_NAME, renamed))
                .as("이름으로는 영원히 안 맞는다")
                .isTrue();
        assertThat(ComplexMatch.matchedByLot(MY_ADDRESS, renamed)).isTrue();
    }

    @Test
    @DisplayName("동이 다르면 이름을 볼 것도 없이 다른 단지다")
    void rejectsAnotherDong() {
        assertThat(ComplexMatch.same(MY_ADDRESS, MY_NAME,
                trade("한화포레나정릉", "석관동", "1037")))
                .as("이름이 똑같아도 동이 다르면 다른 단지다")
                .isFalse();
    }

    /**
     * <b>번지 불일치를 배제 근거로 쓰지 않습니다.</b> 번지가 여러 개인 대단지가
     * 있어서, 다르다고 곧바로 빼면 멀쩡한 거래를 잃습니다.
     */
    @Nested
    @DisplayName("동은 같고 번지가 다르면")
    class SameDongDifferentLot {

        @Test
        @DisplayName("이름이 맞으면 채택한다 — 대단지는 번지가 여럿이다")
        void keepsWhenTheNameMatches() {
            assertThat(ComplexMatch.same(MY_ADDRESS, MY_NAME,
                    trade("한화포레나정릉", "정릉동", "1038")))
                    .isTrue();
        }

        @Test
        @DisplayName("이름도 다르면 뺀다")
        void dropsWhenTheNameDiffersToo() {
            assertThat(ComplexMatch.same(MY_ADDRESS, MY_NAME,
                    trade("정릉힐스테이트", "정릉동", "1038")))
                    .isFalse();
        }

        @Test
        @DisplayName("주소로 잡힌 것은 아니다")
        void isNotALotMatch() {
            assertThat(ComplexMatch.matchedByLot(MY_ADDRESS,
                    trade("한화포레나정릉", "정릉동", "1038")))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("주소를 못 가리면 이름으로")
    class WithoutAddress {

        @Test
        @DisplayName("매물에 번지가 없으면 이름으로 판단한다")
        void fallsBackWhenMineIsMissing() {
            assertThat(ComplexMatch.same("서울시 성북구 정릉동", MY_NAME,
                    trade("한화포레나정릉", "정릉동", "1037"))).isTrue();
            assertThat(ComplexMatch.same("서울시 성북구 정릉동", MY_NAME,
                    trade("정릉꿈에그린", "정릉동", "1037")))
                    .as("주소가 없으면 옛 이름을 잡을 길이 없다")
                    .isFalse();
        }

        @Test
        @DisplayName("거래에 동·번지가 없으면 이름으로 판단한다")
        void fallsBackWhenTheirsIsMissing() {
            assertThat(ComplexMatch.same(MY_ADDRESS, MY_NAME,
                    trade("한화포레나정릉", null, null))).isTrue();
        }
    }

    @Nested
    @DisplayName("이름 규칙은 그대로 (설계 I230)")
    class NameRule {

        @Test
        @DisplayName("괄호와 '아파트'를 걷어내고 견준다")
        void stillNormalizes() {
            assertThat(ComplexMatch.same("서울시 노원구 상계동 692", "상계주공7단지",
                    trade("상계주공7(고층)", "상계동", "700")))
                    .as("번지가 달라도 이름이 맞으면 채택한다")
                    .isTrue();
        }

        /** 이름을 못 가리면 <b>같다고 봅니다</b> — 빼면 이름 없는 거래를 통째로 잃습니다 */
        @Test
        @DisplayName("이름이 너무 짧으면 이름으로 가리지 않는다")
        void ignoresUnusableNames() {
            assertThat(ComplexMatch.sameName("가", "무슨단지")).isTrue();
            assertThat(ComplexMatch.sameName(null, "무슨단지")).isTrue();
        }
    }

    @Test
    @DisplayName("거래가 없으면 아니다")
    void handlesNull() {
        assertThat(ComplexMatch.same(MY_ADDRESS, MY_NAME, null)).isFalse();
        assertThat(ComplexMatch.matchedByLot(MY_ADDRESS, null)).isFalse();
    }

    private ReferenceTrade trade(String name, String dong, String jibun) {
        return new ReferenceTrade(name, 700_000_000L, new BigDecimal("84.9"), 5,
                LocalDate.now(), dong, jibun);
    }
}
