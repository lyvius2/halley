package banghak.home.halley.domain.forecast;

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
