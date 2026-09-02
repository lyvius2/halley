package banghak.home.halley.domain.property;

import java.util.Locale;
import java.util.Optional;

/**
 * 두 매물이 <b>같은 단지</b>인가를 정하는 하나의 열쇠 (설계 I266).
 *
 * <p>지금까지 매물은 저마다 고유했고, 같은 단지의 102동과 104동은 <b>아무 관계도
 * 없는 남</b>이었습니다. 그래서 국토부 실거래를 <b>매물마다</b> 받았습니다 —
 * 같은 단지 같은 평형인데도요.
 *
 * <pre>
 * 한화포레나정릉 102동  ┐
 * 한화포레나정릉 104동  ┼→  같은 단지 · 같은 84.9㎡  →  국토부 한 번
 * 한화포레나정릉 1503호 ┘
 * </pre>
 *
 * <h4>무엇으로 가르는가</h4>
 *
 * <p>이름과 <b>동·번지</b>입니다. 이름만으로는 `래미안` 이 전국에 있고,
 * 번지만으로는 한 번지에 단지가 둘인 곳이 있습니다.
 *
 * <p>이름은 {@link ComplexName} 의 규칙을 그대로 씁니다 — <b>같은 일을 두 곳에서
 * 다르게 하다</b> 갈라진 것이 [I230]이었습니다. 되풀이하지 않습니다.
 *
 * <p>주소를 못 읽으면 <b>이름만으로</b> 묶습니다. 그것이 아무 것도 안 묶는 것보다
 * 낫습니다 — 다만 열쇠에 그 사실을 남겨, 나중에 주소가 채워지면 <b>다른 단지로
 * 갈라져 새로 만들어집니다.</b> 섞이는 것보다 낫습니다.
 */
public record ComplexKey(String value) {

    /** 주소를 못 읽었다는 표시. 나중에 주소가 붙으면 열쇠가 달라진다 */
    private static final String NO_ADDRESS = "?";

    public static ComplexKey of(String name, String addressJibun) {
        final String normalized = Optional.ofNullable(ComplexName.normalize(name))
                .orElseGet(() -> name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
        final String lot = JibunAddress.of(addressJibun)
                .map(a -> a.legalDong() + ":" + a.bonbun() + "-" + a.bubun())
                .orElse(NO_ADDRESS);
        return new ComplexKey(normalized + "|" + lot);
    }

    /** 주소 없이 이름만으로 묶인 것인가 — 나중에 합칠 후보다. */
    public boolean addressUnknown() {
        return value.endsWith("|" + NO_ADDRESS);
    }
}
