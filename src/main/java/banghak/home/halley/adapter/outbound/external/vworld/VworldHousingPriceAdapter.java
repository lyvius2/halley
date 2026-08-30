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

/**
 * V-World 공시가격 속성조회 어댑터 (설계 I54).
 *
 * <p>이 API는 인증 실패도 HTTP 200으로 돌려주고 본문에 `resultCode`를 담는다. 그래서 Feign 예외만으로는
 * 실패를 알 수 없어 본문을 먼저 확인한다. 응답 래퍼 이름(`response`·`apartHousingPrices`)과 항목 배열 키는
 * 서비스·상황마다 다르므로 <b>루트 아래 첫 배열</b>을 항목 목록으로 삼는다.
 *
 * <p><b>연도를 반드시 지정한다.</b> `stdrYear` 없이 부르면 그 필지의 전 연도가 <b>오래된 순으로</b> 나온다.
 * 실측(은마아파트 PNU)에서 `totalCount = 110,600 = 4,424세대 × 25년`이었고 첫 페이지가 2006년치였다.
 * 그대로 쓰면 20년 전 공시가격이 저장된다.
 */
@Slf4j
@Component
public class VworldHousingPriceAdapter implements HousingPricePort {

    /** API 최대치. 대단지는 한 해에도 수천 세대가 나와 페이지를 넘겨야 한다. */
    private static final int MAX_ROWS = 1000;
    /**
     * 한 필지의 한 해 자료가 세대 수보다 많이 나온다 — 실측(은마 4,424세대)에서 `totalCount = 8,848`로
     * <b>세대 수의 2배</b>였습니다. 5페이지로 잡았을 때 56%만 받아 나머지가 조용히 잘렸습니다(설계 I70).
     * 잘리면 특정 면적대가 통째로 빠져 엉뚱한 값이 붙을 수 있으므로 넉넉히 잡고, 그래도 모자라면 경고합니다.
     */
    private static final int MAX_PAGES = 15;
    /** 공동주택가격 공시는 매년 4월 말이라, 연초에는 올해 자료가 아직 없다. 최대 이만큼 거슬러 본다. */
    private static final int YEAR_LOOKBACK = 2;

    /** 공동주택은 `pblntfPc`(공시가격), 개별주택은 `housePc`(주택가격)로 필드명이 다르다. */
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
            // 잘린 채로 조용히 넘어가면 특정 면적대가 빠진 줄 모르고 값을 쓴다
            log.warn("VWorld price lookup truncated - some unit types may be missing. "
                            + "kind={}, pnu={}, stdrYear={}, totalCount={}, collected={}, maxPages={}",
                    kind, pnu, year, total, prices.size(), MAX_PAGES);
        } else {
            log.info("VWorld price lookup done. kind={}, pnu={}, stdrYear={}, totalCount={}, collected={}",
                    kind, pnu, year, total, prices.size());
        }
        return prices;
    }

    /**
     * 자료가 있는 가장 최근 연도를 찾는다. `numOfRows=1`로 `totalCount`만 보므로 호출 비용이 작다.
     */
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
            // totalCount 0이면 그 필지·연도에 자료가 없는 것이고, 0이 아닌데 비었으면 응답 구조를 잘못 읽은 것이다
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

    /**
     * 정상 응답은 `resultCode`가 <b>빈 문자열</b>로 온다(`{"apartHousingPrices": {"resultCode": "", …}}`).
     * 인증·파라미터 오류일 때만 `INVALID_KEY` 같은 코드가 채워지므로, 값이 있고 성공 코드가 아닐 때만 거절로 본다.
     */
    private boolean isRejected(JsonNode wrapper) {
        final String resultCode = wrapper.path("resultCode").asString(null);
        if (resultCode == null || resultCode.isBlank()) {
            return false;
        }
        return !"NORMAL_SERVICE".equals(resultCode) && !"00".equals(resultCode);
    }

    /** 응답은 `{"apartHousingPrices": {...}}` 또는 `{"response": {...}}`처럼 래퍼 하나로 감싸여 온다. */
    private JsonNode firstObjectChild(JsonNode root) {
        for (final Map.Entry<String, JsonNode> entry : root.properties()) {
            if (entry.getValue().isObject()) {
                return entry.getValue();
            }
        }
        return root;
    }

    /** 항목 배열의 키(`field`)가 서비스마다 흔들릴 수 있어 이름 대신 타입으로 찾는다. */
    private JsonNode firstArrayChild(JsonNode wrapper) {
        for (final Map.Entry<String, JsonNode> entry : wrapper.properties()) {
            if (entry.getValue().isArray()) {
                return entry.getValue();
            }
            // 결과가 1건이면 배열이 아니라 객체로 오는 서비스가 있다
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

    /** 페이지 단위 호출 — 공동주택·개별주택 두 엔드포인트를 같은 흐름으로 다루기 위한 것. */
    @FunctionalInterface
    private interface PageCall {
        String get(String stdrYear, int numOfRows, int pageNo);
    }

    private record YearCount(String year, int totalCount) {
    }
}
