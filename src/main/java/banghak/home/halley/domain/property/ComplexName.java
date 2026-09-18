package banghak.home.halley.domain.property;

public final class ComplexName {

    /** 이보다 짧으면 이름으로 안 본다 — `가`·`A` 로는 아무 단지나 걸린다.  */
    private static final int MIN_LENGTH = 2;

    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        final String normalized = name
                // 괄호는 안의 내용까지 버린다 — 국토부가 동 높이·차수를 여기 적는다
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("아파트|APT|apt", "")
                .replaceAll("[\\s·\\-]", "")
                .toLowerCase(java.util.Locale.ROOT);
        return normalized.length() < MIN_LENGTH ? null : normalized;
    }

    public static boolean same(String left, String right) {
        final String a = normalize(left);
        final String b = normalize(right);
        return a != null && b != null && (a.contains(b) || b.contains(a));
    }

    /** 이름으로 가릴 수 있는 쌍인가 — 둘 다 쓸 만한 이름일 때만 참.  */
    public static boolean comparable(String left, String right) {
        return normalize(left) != null && normalize(right) != null;
    }

    private ComplexName() {
    }
}
