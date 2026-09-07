package banghak.home.halley.domain.budget;

public record ProductPreview(String productName, Long priceWon, boolean found) {
    public static ProductPreview empty() { return new ProductPreview(null, null, false); }
}
