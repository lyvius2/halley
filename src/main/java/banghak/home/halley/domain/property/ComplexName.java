package banghak.home.halley.domain.property;

/**
 * 단지명을 견주는 규칙 (설계 I230).
 *
 * <p><b>같은 일을 두 곳에서 다르게 하고 있었습니다.</b> 실거래 카드는 괄호와
 * 그 안의 내용을 통째로 지웠고, 가격 전망은 <b>괄호 기호만</b> 지웠습니다.
 *
 * <pre>
 * 국토부  상계주공7(고층)
 * 카드    → 상계주공7      →  "상계주공7단지" 와 포함 관계  ✅
 * 전망    → 상계주공7고층   →  포함 관계가 아니다          ❌  자료 부족
 * </pre>
 *
 * <p>국토부는 같은 단지를 <b>동 높이·차수로 갈라</b> 적습니다 —
 * `(고층)` · `(저층)` · `(1단지)` · `(A)`. 그게 우리 매물명에는 없으니
 * <b>괄호 안은 통째로 버려야</b> 합니다.
 */
public final class ComplexName {

    /** 이보다 짧으면 이름으로 안 본다 — `가`·`A` 로는 아무 단지나 걸린다. */
    private static final int MIN_LENGTH = 2;

    /**
     * 견줄 수 있는 꼴로 (설계 I230).
     *
     * @return 너무 짧거나 비었으면 <b>null</b> — 이름으로 가릴 수 없다는 뜻이다
     */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        final String normalized = name
                // 괄호는 <b>안의 내용까지</b> 버린다 — 국토부가 동 높이·차수를 여기 적는다
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("아파트|APT|apt", "")
                .replaceAll("[\\s·\\-]", "")
                .toLowerCase(java.util.Locale.ROOT);
        return normalized.length() < MIN_LENGTH ? null : normalized;
    }

    /**
     * 같은 단지로 볼 것인가.
     *
     * <p><b>서로 포함이면 같게 봅니다</b> — `래미안` 과 `래미안아파트`,
     * `상계주공7단지` 와 `상계주공7` 은 같은 곳입니다.
     *
     * @return 둘 중 하나라도 이름을 못 가리면 <b>false</b>. 부르는 쪽이
     *         "이름으로는 모른다"와 "다른 단지다"를 갈라 다뤄야 한다
     */
    public static boolean same(String left, String right) {
        final String a = normalize(left);
        final String b = normalize(right);
        return a != null && b != null && (a.contains(b) || b.contains(a));
    }

    /** 이름으로 가릴 수 있는 쌍인가 — 둘 다 쓸 만한 이름일 때만 참. */
    public static boolean comparable(String left, String right) {
        return normalize(left) != null && normalize(right) != null;
    }

    private ComplexName() {
    }
}
