package banghak.home.halley.domain.landuse;

/** 필지와 지역·지구의 관계. */
public enum LandUseConflict {

 /** 필지 전체가 그 안에 있음. 이것만 실제로 적용된다. */
    INCLUDED("포함"),

 /** 일부만 걸침. 도로·공원 계획선이 지나가는 경우. */
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

 /** V-World 응답의 cnflcAtNm을 매핑한다. 모르는 값은 '저촉'으로 보수적으로 본다. */
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
