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
    /**
     * 필지고유번호(PNU, 19자리) — 공시가격 조회 키다 (설계 I54).
     * 구성: 법정동코드(10) + 필지구분(1: 일반 1 / 산 2) + 본번(4) + 부번(4). 하나라도 없으면 만들 수 없다.
     */
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
                    document.path("address").path("b_code").asString(null),
                    pnu(document.path("address"))));
        }
        return results;
    }
}
