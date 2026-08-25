package banghak.home.halley.domain.geo;

import java.math.BigDecimal;

public record GeoSearchResult(
        String addressName,
        String roadAddressName,
        BigDecimal lat,
        BigDecimal lng
) {
}
