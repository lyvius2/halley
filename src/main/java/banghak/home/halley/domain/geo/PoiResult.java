package banghak.home.halley.domain.geo;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public record PoiResult(
        String name,
        String categoryGroupCode,
        Integer distanceM,
        String x,
        String y
) {
    public static PoiResult of(String name, String categoryGroupCode, Integer distanceM, String x, String y) {
        return new PoiResult(name, categoryGroupCode, distanceM, x, y);
    }

    public static List<PoiResult> mapPois(JsonNode root) {
        final List<PoiResult> results = new ArrayList<>();
        for (final JsonNode document : root.path("documents")) {
            final String distance = document.path("distance").asString(null);
            results.add(PoiResult.of(
                    document.path("place_name").asString(null),
                    document.path("category_group_code").asString(null),
                    distance == null ? null : Integer.parseInt(distance),
                    document.path("x").asString(null),
                    document.path("y").asString(null)));
        }
        return results;
    }
}
