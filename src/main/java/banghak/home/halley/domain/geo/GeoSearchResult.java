package banghak.home.halley.domain.geo;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record GeoSearchResult(
        String addressName,
        String roadAddressName,
        BigDecimal lat,
        BigDecimal lng,
        String legalDongCode
) {
    /**
     * 카카오 주소검색 응답에서 도로명주소는 {@code road_address.address_name}에,
     * 법정동코드는 {@code address.b_code}(10자리)에 들어 있다. 앞 5자리가 국토부 API의 `LAWD_CD`다.
     */
    public static List<GeoSearchResult> mapDocuments(JsonNode root) {
        final List<GeoSearchResult> results = new ArrayList<>();
        for (final JsonNode document : root.path("documents")) {
            final String y = document.path("y").asString(null);
            final String x = document.path("x").asString(null);
            results.add(new GeoSearchResult(
                    document.path("address_name").asString(null),
                    document.path("road_address").path("address_name").asString(null),
                    y == null ? null : new BigDecimal(y),
                    x == null ? null : new BigDecimal(x),
                    document.path("address").path("b_code").asString(null)));
        }
        return results;
    }
}
