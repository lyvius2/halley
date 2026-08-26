package banghak.home.halley.ingest.parser;

public record ParseResult<T>(
        T value,
        Confidence confidence,
        String rawSnippet,
        String note
) {

    public static <T> ParseResult<T> of(T value, String rawSnippet) {
        return new ParseResult<>(value, Confidence.EXACT, rawSnippet, null);
    }

    public static <T> ParseResult<T> derived(T value, String rawSnippet, String note) {
        return new ParseResult<>(value, Confidence.DERIVED, rawSnippet, note);
    }

    public static <T> ParseResult<T> missing() {
        return new ParseResult<>(null, Confidence.MISSING, null, null);
    }

    public boolean isPresent() {
        return confidence != Confidence.MISSING;
    }
}
