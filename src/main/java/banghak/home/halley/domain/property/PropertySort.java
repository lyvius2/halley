package banghak.home.halley.domain.property;

/** 매물 목록을 무엇으로 줄 세울까. */
public enum PropertySort {

 /** 아직 안 가 본 곳이 먼저, 그 안에서 추천점수가 높은 순 */
    DEFAULT,
 /** 매매가·보증금이 낮은 순 */
    PRICE,
 /** 전용면적이 넓은 순 */
    AREA,
 /** 추천점수가 높은 순 */
    SCORE,
 /** 직주근접 점수가 높은 순 */
    COMMUTE;

 /** 모르는 값이 오면 기본으로. 화면이 오래된 채로 떠 있어도 목록은 나와야 한다 */
    public static PropertySort of(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        for (final PropertySort candidate : values()) {
            if (candidate.name().equalsIgnoreCase(raw.trim())) {
                return candidate;
            }
        }
        return DEFAULT;
    }
}
