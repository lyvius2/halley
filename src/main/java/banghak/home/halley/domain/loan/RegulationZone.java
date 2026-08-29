package banghak.home.halley.domain.loan;

/**
 * 부동산 규제지역 구분 (설계 I66).
 *
 * <p>국토교통부 고시로 지정·해제되며, 구분에 따라 LTV 비율이 달라집니다.
 * 지정 현황은 공개 API가 없어 `regulated_area` 테이블로 관리합니다(설계 I64의 규제 데이터 원칙).
 */
public enum RegulationZone {

    /** 규제지역이 아님. 기본값 — 지정 정보가 없으면 여기로 본다. */
    NORMAL("비규제지역"),

    /** 조정대상지역. */
    ADJUSTMENT_TARGET("조정대상지역"),

    /** 투기과열지구. 규제 강도가 가장 높다. */
    SPECULATION_OVERHEATED("투기과열지구");

    private final String label;

    RegulationZone(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 규제 파라미터 키에 쓰는 조각 — `ltv.rate.{segment}.{ownership}` */
    public String segment() {
        return switch (this) {
            case NORMAL -> "normal";
            case ADJUSTMENT_TARGET -> "adjustment";
            case SPECULATION_OVERHEATED -> "speculation";
        };
    }
}
