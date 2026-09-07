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
        String legalDongCode,
        String pnu
) {
 /** 필지고유번호(PNU, 19자리). 공시가격 조회 키다. */
    static String pnu(JsonNode address) {
        final String bCode = address.path("b_code").asString(null);
        final String main = address.path("main_address_no").asString(null);
        if (bCode == null || bCode.length() != 10 || main == null || main.isBlank()) {
            return null;
        }
        final String sub = address.path("sub_address_no").asString("");
        final boolean mountain = "Y".equalsIgnoreCase(address.path("mountain_yn").asString(""));
        try {
            return bCode
                    + (mountain ? "2" : "1")
                    + String.format("%04d", Integer.parseInt(main.trim()))
                    + String.format("%04d", sub == null || sub.isBlank() ? 0 : Integer.parseInt(sub.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

 /** 카카오 주소검색 응답에서 도로명주소는 road_address.address_name에, */
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
                    document.path("address").path("b_code").asString(null),
                    pnu(document.path("address"))));
        }
        return results;
    }
}
