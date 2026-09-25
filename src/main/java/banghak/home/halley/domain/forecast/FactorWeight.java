package banghak.home.halley.domain.forecast;

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
