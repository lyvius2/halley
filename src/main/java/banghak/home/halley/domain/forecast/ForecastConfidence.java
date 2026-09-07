package banghak.home.halley.domain.forecast;

/** 이 판단을 얼마나 믿을 만한가. */
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
