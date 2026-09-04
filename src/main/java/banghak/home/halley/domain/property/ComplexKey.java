package banghak.home.halley.domain.property;

import java.util.Locale;
import java.util.Optional;

/**
 * 두 매물이 같은 단지인가를 정하는 열쇠 (설계 I266) — 이름과 동·번지로 가른다.
 * 이름 정규화는 {@link ComplexName} 의 규칙을 그대로 쓴다. 주소를 못 읽으면
 * 이름만으로 묶되, 나중에 주소가 채워지면 다른 단지로 갈라져 새로 만들어진다.
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
