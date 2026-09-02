package banghak.home.halley.domain.property;

/**
 * 매물 목록을 무엇으로 줄 세울까 (설계 I240).
 *
 * <p>[I221]에서 <b>화면이 정하게</b> 했습니다. 목록에 필요한 값이 이미 전부 실려
 * 있었으니 그때는 맞는 판단이었습니다. 서버가 30건씩 잘라 보내기 시작하면
 * <b>더는 맞지 않습니다</b> — 30건 안에서만 줄 세우면 2쪽의 1등이 1쪽의 꼴찌보다
 * 앞설 수 있습니다.
 *
 * <p>줄 세우는 곳과 자르는 곳은 <b>같아야</b> 합니다.
 */
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

    /** 모르는 값이 오면 기본으로 — 화면이 오래된 채로 떠 있어도 목록은 나와야 한다 */
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
