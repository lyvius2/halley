package banghak.home.halley.domain.landuse;

/**
 * 필지와 지역·지구의 관계 (설계 I69).
 *
 * <p><b>이 값을 구분하지 않으면 오해를 만듭니다.</b> 은마아파트 필지 실측에서 용도지역이
 * 제1종·제2종·제3종일반주거지역 세 개로 나왔는데, 실제 적용되는 것은 <b>제3종(포함)</b> 하나이고
 * 1·2종은 옆 필지입니다. 다 보여주면 "이 집이 세 용도지역에 걸쳐 있다"로 읽힙니다.
 */
public enum LandUseConflict {

    /** 필지 전체가 그 안에 있음. <b>이것만 실제로 적용된다.</b> */
    INCLUDED("포함"),

    /** 일부만 걸침 — 도로·공원 계획선이 지나가는 경우. */
    OVERLAP("저촉"),

    /** 인접해 있을 뿐 적용되지 않음. */
    ADJACENT("접함");

    private final String label;

    LandUseConflict(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** V-World 응답의 `cnflcAtNm`을 매핑한다. 모르는 값은 '저촉'으로 보수적으로 본다. */
    public static LandUseConflict fromLabel(String value) {
        if (value == null) {
            return OVERLAP;
        }
        return switch (value.trim()) {
            case "포함" -> INCLUDED;
            case "접함" -> ADJACENT;
            default -> OVERLAP;
        };
    }
}
