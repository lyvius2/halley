package banghak.home.halley.domain.property;

public record ListingCheckResult(
        ListingVerdict verdict,
        String evidence,
        Integer httpStatus
) {

    public static ListingCheckResult of(ListingVerdict verdict, String evidence, Integer httpStatus) {
        return new ListingCheckResult(verdict, evidence, httpStatus);
    }
}
