package banghak.home.halley.domain.loan;

/**
 * 대출 신청 시점의 주택 보유 상태 (설계 I66).
 *
 * <p>LTV는 보유 주택 수에 따라 크게 달라집니다. 규제지역의 다주택자는 아예 0%인 시기도 있었습니다.
 */
public enum HouseOwnership {

    /** 무주택. */
    NONE("무주택", "none"),

    /** 1주택 — 보통 기존 주택 처분 조건이 붙는다. */
    ONE("1주택", "one"),

    /** 2주택 이상. */
    MULTI("다주택", "multi");

    private final String label;
    private final String segment;

    HouseOwnership(String label, String segment) {
        this.label = label;
        this.segment = segment;
    }

    public String label() {
        return label;
    }

    /** 규제 파라미터 키에 쓰는 조각 — `ltv.rate.{zone}.{segment}` */
    public String segment() {
        return segment;
    }

    /** 보유 주택 수를 구간으로 접는다. 음수·null은 무주택으로 본다. */
    public static HouseOwnership of(Integer ownedHouseCount) {
        if (ownedHouseCount == null || ownedHouseCount <= 0) {
            return NONE;
        }
        return ownedHouseCount == 1 ? ONE : MULTI;
    }
}
