package banghak.home.halley.adapter.outbound.external.vworld;

import banghak.home.halley.application.port.out.external.HousingPricePort;
import banghak.home.halley.domain.property.OfficialPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** V-World 공시가격 속성조회 어댑터. */
@Slf4j
@Component
public class VworldHousingPriceAdapter implements HousingPricePort {

 /** API 최대치. 대단지는 한 해에도 수천 세대가 나와 페이지를 넘겨야 한다. */
    private static final int MAX_ROWS = 1000;
 /** 한 필지의 한 해 자료가 세대 수보다 많이 나온다. 실측(은마 4,424세대)에서 totalCount = 8,848로 */
    private static final int MAX_PAGES = 15;
 /** 공동주택가격 공시는 매년 4월 말이라, 연초에는 올해 자료가 아직 없다. 최대 이만큼 거슬러 본다. */
    private static final int YEAR_LOOKBACK = 2;

 /** 공동주택은 pblntfPc(공시가격), 개별주택은 housePc(주택가격)로 필드명이 다르다. */
    private static final List<String> PRICE_KEYS = List.of("pblntfPc", "housePc", "pblntfPclnd");
    private static final List<String> AREA_KEYS = List.of("prvuseAr", "ladRegstrAr", "bildngAr");

    private final VworldHousingPriceFeignClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public VworldHousingPriceAdapter(VworldHousingPriceFeignClient client,
                                     ObjectMapper objectMapper,
                                     @Value("${vworld.api-key:}") String apiKey) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public List<OfficialPrice> fetchApartmentPrices(String pnu) {
        return fetch(pnu, "apartment", (year, rows, page) ->
                client.fetchApartmentPrice(apiKey, pnu, year, "json", rows, page));
    }

    @Override
    public List<OfficialPrice> fetchDetachedHousePrices(String pnu) {
        return fetch(pnu, "detached-house", (year, rows, page) ->
                client.fetchDetachedHousePrice(apiKey, pnu, year, "json", rows, page));
    }

 /** 연도 하나를 정한 뒤 그 해 자료만 모은다. */
    private List<OfficialPrice> fetch(String pnu, String kind, PageCall call) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("Skipping VWorld price lookup - api key not configured. kind={}", kind);
            return List.of();
        }
        if (pnu == null || pnu.length() != 19) {
            log.info("Skipping VWorld price lookup - invalid PNU. kind={}, pnu={}", kind, pnu);
            return List.of();
        }
        final Optional<YearCount> resolved = resolveYear(pnu, kind, call);
        if (resolved.isEmpty()) {
            return List.of();
        }
        final String year = resolved.get().year();
        final int total = resolved.get().totalCount();
        final List<OfficialPrice> prices = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES && prices.size() < total; page++) {
            final String body = call.get(year, MAX_ROWS, page);
            if (body == null) {
                break;
            }
            final List<OfficialPrice> parsed = parse(body, pnu, kind);
            if (parsed.isEmpty()) {
                break;
            }
            prices.addAll(parsed);
        }
        if (prices.size() < total) {
            log.warn("VWorld price lookup truncated - some unit types may be missing. "
                            + "kind={}, pnu={}, stdrYear={}, totalCount={}, collected={}, maxPages={}",
                    kind, pnu, year, total, prices.size(), MAX_PAGES);
        } else {
            log.info("VWorld price lookup done. kind={}, pnu={}, stdrYear={}, totalCount={}, collected={}",
                    kind, pnu, year, total, prices.size());
        }
        return prices;
    }

 /** 자료가 있는 가장 최근 연도를 찾는다. numOfRows=1로 totalCount만 보므로 호출 비용이 작다. */
    private Optional<YearCount> resolveYear(String pnu, String kind, PageCall call) {
        final int thisYear = Year.now().getValue();
        for (int year = thisYear; year >= thisYear - YEAR_LOOKBACK; year--) {
            final String body = call.get(String.valueOf(year), 1, 1);
            if (body == null) {
                return Optional.empty();
            }
            final JsonNode wrapper = wrapperOf(body);
            if (wrapper == null) {
                return Optional.empty();
            }
            if (isRejected(wrapper)) {
                log.warn("VWorld price lookup rejected. kind={}, pnu={}, resultCode={}, resultMsg={}",
                        kind, pnu, wrapper.path("resultCode").asString(null),
                        wrapper.path("resultMsg").asString(null));
                return Optional.empty();
            }
            final int total = asInt(wrapper.path("totalCount").asString(null), 0);
            if (total > 0) {
                return Optional.of(new YearCount(String.valueOf(year), total));
            }
        }
        log.info("No VWorld price data for recent years. kind={}, pnu={}, from={}, lookback={}",
                kind, pnu, thisYear, YEAR_LOOKBACK);
        return Optional.empty();
    }

    List<OfficialPrice> parse(String body, String pnu, String kind) {
        final JsonNode wrapper = wrapperOf(body);
        if (wrapper == null) {
            return List.of();
        }
        if (isRejected(wrapper)) {
            log.warn("VWorld price lookup rejected. kind={}, pnu={}, resultCode={}, resultMsg={}",
                    kind, pnu, wrapper.path("resultCode").asString(null),
                    wrapper.path("resultMsg").asString(null));
            return List.of();
        }
        final JsonNode items = firstArrayChild(wrapper);
        if (items == null) {
            log.info("VWorld price lookup returned no items. kind={}, pnu={}, totalCount={}",
                    kind, pnu, wrapper.path("totalCount").asString("?"));
            return List.of();
        }
        final List<OfficialPrice> prices = new ArrayList<>();
        for (final JsonNode item : items) {
            final Long price = asLong(firstText(item, PRICE_KEYS));
            if (price == null) {
                continue;
            }
            prices.add(new OfficialPrice(
                    price,
                    asInt(item.path("stdrYear").asString(null), 0) == 0
                            ? null : asInt(item.path("stdrYear").asString(null), 0),
                    item.path("dongNm").asString(null),
                    item.path("hoNm").asString(null),
                    asDecimal(firstText(item, AREA_KEYS))));
        }
        return prices;
    }

    private JsonNode wrapperOf(String body) {
        try {
            return firstObjectChild(objectMapper.readTree(body));
        } catch (RuntimeException e) {
            log.warn("Failed to parse VWorld price response. cause={}", e.getMessage());
            return null;
        }
    }

 /** 정상 응답은 resultCode가 빈 문자열로 온다({"apartHousingPrices": {"resultCode": "", …}}). */
    private boolean isRejected(JsonNode wrapper) {
        final String resultCode = wrapper.path("resultCode").asString(null);
        if (resultCode == null || resultCode.isBlank()) {
            return false;
        }
        return !"NORMAL_SERVICE".equals(resultCode) && !"00".equals(resultCode);
    }

 /** 응답은 {"apartHousingPrices": {...}} 또는 {"response": {...}}처럼 래퍼 하나로 감싸여 온다. */
    private JsonNode firstObjectChild(JsonNode root) {
        for (final Map.Entry<String, JsonNode> entry : root.properties()) {
            if (entry.getValue().isObject()) {
                return entry.getValue();
            }
        }
        return root;
    }

 /** 항목 배열의 키(field)가 서비스마다 흔들릴 수 있어 이름 대신 타입으로 찾는다. */
    private JsonNode firstArrayChild(JsonNode wrapper) {
        for (final Map.Entry<String, JsonNode> entry : wrapper.properties()) {
            if (entry.getValue().isArray()) {
                return entry.getValue();
            }
            if (entry.getValue().isObject() && !entry.getValue().path("pnu").isMissingNode()) {
                return objectMapper.createArrayNode().add(entry.getValue());
            }
        }
        return null;
    }

    private String firstText(JsonNode item, List<String> keys) {
        for (final String key : keys) {
            final String value = item.path(key).asString(null);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private Long asLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int asInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private BigDecimal asDecimal(String value) {
        try {
            return value == null ? null : new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

 /** 페이지 단위 호출. 공동주택·개별주택 두 엔드포인트를 같은 흐름으로 다루기 위한 것. */
    @FunctionalInterface
    private interface PageCall {
        String get(String stdrYear, int numOfRows, int pageNo);
    }

    private record YearCount(String year, int totalCount) {
    }
}
