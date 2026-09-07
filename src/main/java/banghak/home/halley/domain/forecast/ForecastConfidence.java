package banghak.home.halley.domain.forecast;

/**
 * 이 판단을 얼마나 믿을 만한가.
 *
 * 코드 예측에서는 요인들이 서로 얼마나 일치하는가로 정합니다.
 * 방향이 갈리는데 확신이 높을 수는 없습니다.
 */
public enum ForecastConfidence {
    LOW("낮음"),
    MEDIUM("보통"),
    HIGH("높음");

    private final String label;

    ForecastConfidence(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
