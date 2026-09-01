package banghak.home.halley.domain.property;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 단지명 견주기 (설계 I230).
 *
 * <p>같은 일을 두 곳에서 다르게 하다 갈라졌습니다. 실거래 카드는 괄호와 내용을
 * 통째로 지웠고, 가격 전망은 <b>괄호 기호만</b> 지웠습니다 —
 * `상계주공7(고층)` 이 `상계주공7고층` 이 되어 `상계주공7단지` 와 안 맞았고,
 * 그 단지는 전망이 <b>늘 자료 부족</b>이었습니다.
 */
@DisplayName("단지명 견주기 (설계 I230)")
class ComplexNameTest {

    /**
     * 국토부는 같은 단지를 <b>동 높이·차수로 갈라</b> 적습니다.
     * 우리 매물명에는 그 꼬리가 없습니다.
     */
    @ParameterizedTest
    @CsvSource({
            "상계주공7단지,      상계주공7(고층)",
            "상계주공7단지,      상계주공7(저층)",
            "래미안석관,        래미안석관아파트",
            "석관신동아파밀리에,  신동아파밀리에",
            "한화포레나정릉,     한화포레나정릉(1단지)"
    })
    @DisplayName("괄호 안은 통째로 버린다 — 국토부가 거기에 동 높이를 적는다")
    void sameComplexAcrossNotation(String mine, String theirs) {
        assertThat(ComplexName.same(mine, theirs)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "상계주공7단지,  상계주공5단지",
            "래미안석관,    자이석관",
            "한화포레나정릉, 포레나노원"
    })
    @DisplayName("다른 단지는 다르다 — 괄호를 버렸다고 아무거나 맞으면 안 된다")
    void differentComplexesStayDifferent(String mine, String theirs) {
        assertThat(ComplexName.same(mine, theirs)).isFalse();
    }

    /**
     * <b>`가`·`A` 로는 아무 단지나 걸립니다.</b> 이름으로 못 가리는 경우와
     * "다른 단지"를 부르는 쪽이 갈라 다뤄야 합니다.
     */
    @Test
    @DisplayName("너무 짧은 이름은 이름으로 안 본다")
    void tooShortIsNotAName() {
        assertThat(ComplexName.normalize("가")).isNull();
        assertThat(ComplexName.normalize("(고층)")).isNull();
        assertThat(ComplexName.normalize("아파트")).isNull();
        assertThat(ComplexName.comparable("가", "상계주공7")).isFalse();
    }

    @Test
    @DisplayName("빈 값은 이름이 아니다")
    void blankIsNotAName() {
        assertThat(ComplexName.normalize(null)).isNull();
        assertThat(ComplexName.normalize("   ")).isNull();
        assertThat(ComplexName.same(null, "상계주공7")).isFalse();
        assertThat(ComplexName.same("상계주공7", null)).isFalse();
    }

    @Test
    @DisplayName("공백·가운뎃점·붙임표는 표기 흔들림이다")
    void punctuationIsNoise() {
        assertThat(ComplexName.same("래미안 석관", "래미안·석관")).isTrue();
        assertThat(ComplexName.same("e-편한세상", "e편한세상")).isTrue();
    }
}
