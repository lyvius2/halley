package banghak.home.halley.domain.forecast;

/**
 * 한 요인이 얼마나 무겁게 작용하는가.
 *
 * 숫자가 아니라 등급입니다. 숫자로 두면 합산하고 싶어지는데, 가중치의 근거가
 * 없습니다. 합산하는 순간 그 임의의 숫자가 객관적 예측처럼 보입니다.
 */
public enum FactorWeight {
    HIGH("높음"),
    MEDIUM("보통"),
    LOW("낮음");

    private final String label;

    FactorWeight(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
