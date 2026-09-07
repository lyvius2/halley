package banghak.home.halley.domain.forecast;

/** 한 요인이 얼마나 무겁게 작용하는가. */
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
