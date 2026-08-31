package banghak.home.halley.ingest.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라벨과 값이 한 줄에 붙어 있는 경우 (설계 I159).
 *
 * <p>이 갈래는 <b>잘못 잡기 쉽습니다.</b> 라벨을 줄 아무 데서나 찾으면 문장 속의 숫자를
 * 집고, 라벨 뒤에 글자가 붙은 것을 안 보면 더 긴 라벨을 짧은 라벨로 읽습니다.
 * 그래서 두 방어를 각각 겨눕니다.
 */
@DisplayName("같은 줄 라벨 읽기 (설계 I159)")
class TextDocumentSameLineTest {

    @Test
    @DisplayName("라벨 뒤의 값을 읽는다")
    void readsValueAfterLabelOnSameLine() {
        final TextDocument doc = new TextDocument("대출 금액\nKB시세 7억 4,000만원\n투기과열");

        assertThat(doc.valueOnSameLine("KB시세")).contains("7억 4,000만원");
    }

    /**
     * <p><b>`startsWith` 자체는 이 테스트로 독립 검증되지 않는다.</b> `contains` 로 바꿔도
     * 바로 뒤의 '라벨 뒤 글자' 검사가 같은 줄들을 걸러내서 결과가 같다. 확인해 봤고,
     * 그래서 적어 둔다 — `startsWith` 는 <b>substring 인덱스를 옳게 만드는</b> 쪽이
     * 값어치이지, 관측되는 동작의 차이가 아니다.
     */
    @Test
    @DisplayName("줄 가운데에 낀 라벨은 잡지 않는다 — 문장 속 숫자를 집게 된다")
    void ignoresLabelInTheMiddleOfALine() {
        final TextDocument doc = new TextDocument("이 매물의 KB시세 7억 4,000만원 대비 저렴합니다");

        assertThat(doc.valueOnSameLine("KB시세")).isEmpty();
    }

    @Test
    @DisplayName("라벨 뒤에 글자가 붙으면 다른 라벨이다 — '관리비'와 '관리비부과기준'은 다르다")
    void doesNotMatchWhenLabelIsAPrefixOfAnotherLabel() {
        final TextDocument doc = new TextDocument("관리비부과기준 정액관리비");

        assertThat(doc.valueOnSameLine("관리비")).isEmpty();
    }

    @Test
    @DisplayName("라벨만 있고 값이 없으면 비어 있다")
    void emptyWhenLabelAlone() {
        assertThat(new TextDocument("KB시세").valueOnSameLine("KB시세")).isEmpty();
    }

    @Test
    @DisplayName("없는 라벨은 비어 있다")
    void emptyWhenAbsent() {
        assertThat(new TextDocument("매매가\n7억").valueOnSameLine("KB시세")).isEmpty();
    }
}
