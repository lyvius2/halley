package banghak.home.halley.domain.geo;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record GeoSearchResult(
        String addressName,
        String roadAddressName,
        BigDecimal lat,
        BigDecimal lng
) {
    public static List<GeoSearchResult> mapDocuments(JsonNode root) {
        final List<GeoSearchResult> results = new ArrayList<>();
        for (final JsonNode document : root.path("documents")) {
            final String y = document.path("y").asString(null);
            final String x = document.path("x").asString(null);
            results.add(new GeoSearchResult(
                    document.path("address_name").asString(null),
                    document.path("road_address_name").asString(null),
                    y == null ? null : new BigDecimal(y),
                    x == null ? null : new BigDecimal(x)));
        }
        return results;
    }
}
