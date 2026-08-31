package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastConfidence;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.PriceForecast;
import banghak.home.halley.domain.forecast.PriceOutlook;
import banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastHistoryTable;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.CAVEATS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.CAVEATS_RAW;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.CODE_DIRECTION;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.COMPUTED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.CONFIDENCE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.DIRECTION;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.FACTORS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.FACTORS_RAW;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.HORIZON_MONTHS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.MODEL;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.PROMPT_HASH;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PriceForecastHistoryTable.TABLE_HISTORY;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJson;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJsonNode;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

/**
 * 가격 전망 저장 (설계 I135·I138).
 *
 * <p>{@code price_forecast}는 매물당 <b>최신 한 건</b>만 들고 덮어씁니다 — 목록(I124)이
 * 보는 것이 이쪽이라 "매물별 최신 한 건"을 매번 골라내게 만들지 않습니다.
 *
 * <p>덮어쓴 것은 {@code price_forecast_history}에 <b>쌓아 둡니다</b>(I138).
 * 그러지 않으면 "3개월 전에 오른다고 했는데 실제로 올랐나"를 볼 수 없습니다 —
 * 그 행이 이미 사라지고 없습니다.
 */
@Repository
public class PriceForecastRepository {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public PriceForecastRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    /**
     * 최신 전망을 덮어쓰고, 같은 내용을 이력에도 남긴다 (설계 I138).
     *
     * <p><b>한 자리에서 같이 합니다.</b> 서비스가 두 번 부르게 하면 언젠가 한쪽을 빠뜨리고,
     * 빠진 것은 조용히 지나갑니다 — 몇 달 뒤 이력을 열어 봐야 비어 있는 걸 압니다.
     */
    public PriceForecast upsert(PriceForecast forecast) {
        dsl.transaction(cfg -> {
            final DSLContext tx = cfg.dsl();
            tx.insertInto(TABLE)
                    .set(PROPERTY_ID, forecast.propertyId())
                    .set(columns(forecast))
                    .onConflict(PROPERTY_ID)
                    .doUpdate()
                    .set(columns(forecast))
                    .execute();
            tx.insertInto(TABLE_HISTORY)
                    .set(PriceForecastHistoryTable.PROPERTY_ID, forecast.propertyId())
                    .set(historyColumns(forecast))
                    .execute();
        });
        return findByPropertyId(forecast.propertyId()).orElseThrow();
    }

    /**
     * 이 매물의 전망 이력 — 최근 것부터 (설계 I138).
     *
     * <p>사후 검증(구현 10)의 재료입니다. 판정 기준은 아직 정하지 않았습니다 —
     * <b>표본을 보고 정할 일</b>이라 지금은 쌓기만 합니다.
     */
    public List<PriceForecast> history(Long propertyId) {
        return dsl.selectFrom(TABLE_HISTORY)
                .where(PriceForecastHistoryTable.PROPERTY_ID.eq(propertyId))
                .orderBy(PriceForecastHistoryTable.COMPUTED_AT.desc(),
                        PriceForecastHistoryTable.ID.desc())
                .fetch()
                .map(this::mapHistory);
    }

    /** 최신과 이력이 어긋나지 않도록 <b>한 곳에서</b> 만든다. */
    private java.util.Map<Field<?>, Object> columns(PriceForecast forecast) {
        final PriceOutlook outlook = forecast.outlook();
        final java.util.Map<Field<?>, Object> values = new java.util.LinkedHashMap<>();
        values.put(DIRECTION, outlook.direction().name());
        values.put(CODE_DIRECTION, forecast.codeDirection() == null
                ? null : forecast.codeDirection().name());
        values.put(CONFIDENCE, outlook.confidence().name());
        values.put(HORIZON_MONTHS, outlook.horizonMonths());
        values.put(FACTORS, toJson(factorsJson(outlook.factors()), objectMapper));
        values.put(CAVEATS, toJson(stringsJson(outlook.caveats()), objectMapper));
        values.put(MODEL, forecast.model());
        values.put(PROMPT_HASH, forecast.promptHash());
        values.put(COMPUTED_AT, toOffset(forecast.computedAt()));
        return values;
    }

    private java.util.Map<Field<?>, Object> historyColumns(PriceForecast forecast) {
        final PriceOutlook outlook = forecast.outlook();
        final java.util.Map<Field<?>, Object> values = new java.util.LinkedHashMap<>();
        values.put(PriceForecastHistoryTable.DIRECTION, outlook.direction().name());
        values.put(PriceForecastHistoryTable.CODE_DIRECTION, forecast.codeDirection() == null
                ? null : forecast.codeDirection().name());
        values.put(PriceForecastHistoryTable.CONFIDENCE, outlook.confidence().name());
        values.put(PriceForecastHistoryTable.HORIZON_MONTHS, outlook.horizonMonths());
        values.put(PriceForecastHistoryTable.FACTORS, toJson(factorsJson(outlook.factors()), objectMapper));
        values.put(PriceForecastHistoryTable.CAVEATS, toJson(stringsJson(outlook.caveats()), objectMapper));
        values.put(PriceForecastHistoryTable.MODEL, forecast.model());
        values.put(PriceForecastHistoryTable.PROMPT_HASH, forecast.promptHash());
        values.put(PriceForecastHistoryTable.COMPUTED_AT, toOffset(forecast.computedAt()));
        return values;
    }

    /**
     * 매물 여러 건을 한 번에 (설계 I124).
     *
     * <p>목록이 매물마다 따로 부르면 그 수만큼 왕복이 늘어납니다 — 이미 한 번 겪은 일입니다.
     */
    public java.util.Map<Long, PriceForecast> findByPropertyIds(java.util.Collection<Long> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            return java.util.Map.of();
        }
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.in(propertyIds))
                .fetch()
                .map(this::map)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        PriceForecast::propertyId, f -> f, (a, b) -> a));
    }

    public Optional<PriceForecast> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .fetchOptional()
                .map(this::map);
    }

    private PriceForecast map(Record r) {
        return new PriceForecast(
                r.get(ID),
                r.get(PROPERTY_ID),
                new PriceOutlook(
                        toEnum(ForecastDirection.class, r.get(DIRECTION)),
                        toEnum(ForecastConfidence.class, r.get(CONFIDENCE)),
                        r.get(HORIZON_MONTHS),
                        toFactors(toJsonNode(r.get(FACTORS_RAW), objectMapper)),
                        toStrings(toJsonNode(r.get(CAVEATS_RAW), objectMapper))),
                toEnum(ForecastDirection.class, r.get(CODE_DIRECTION)),
                r.get(PROMPT_HASH),
                r.get(MODEL),
                toInstant(r.get(COMPUTED_AT)));
    }

    private PriceForecast mapHistory(Record r) {
        return new PriceForecast(
                r.get(PriceForecastHistoryTable.ID),
                r.get(PriceForecastHistoryTable.PROPERTY_ID),
                new PriceOutlook(
                        toEnum(ForecastDirection.class, r.get(PriceForecastHistoryTable.DIRECTION)),
                        toEnum(ForecastConfidence.class, r.get(PriceForecastHistoryTable.CONFIDENCE)),
                        r.get(PriceForecastHistoryTable.HORIZON_MONTHS),
                        toFactors(toJsonNode(r.get(PriceForecastHistoryTable.FACTORS_RAW), objectMapper)),
                        toStrings(toJsonNode(r.get(PriceForecastHistoryTable.CAVEATS_RAW), objectMapper))),
                toEnum(ForecastDirection.class, r.get(PriceForecastHistoryTable.CODE_DIRECTION)),
                r.get(PriceForecastHistoryTable.PROMPT_HASH),
                r.get(PriceForecastHistoryTable.MODEL),
                toInstant(r.get(PriceForecastHistoryTable.COMPUTED_AT)));
    }

    private JsonNode factorsJson(List<PriceFactor> factors) {
        final ArrayNode array = objectMapper.createArrayNode();
        if (factors == null) {
            return array;
        }
        for (final PriceFactor factor : factors) {
            final ObjectNode node = array.addObject();
            node.put("name", factor.name());
            node.put("effect", factor.effect().name());
            node.put("weight", factor.weight().name());
            node.put("evidence", factor.evidence());
        }
        return array;
    }

    private List<PriceFactor> toFactors(JsonNode array) {
        final List<PriceFactor> factors = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return factors;
        }
        for (final JsonNode node : array) {
            factors.add(new PriceFactor(
                    node.path("name").asString(null),
                    toEnum(ForecastDirection.class, node.path("effect").asString(null)),
                    toEnum(FactorWeight.class, node.path("weight").asString(null)),
                    node.path("evidence").asString(null)));
        }
        return factors;
    }

    private JsonNode stringsJson(List<String> values) {
        final ArrayNode array = objectMapper.createArrayNode();
        if (values != null) {
            values.forEach(array::add);
        }
        return array;
    }

    private List<String> toStrings(JsonNode array) {
        final List<String> values = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return values;
        }
        for (final JsonNode node : array) {
            values.add(node.asString(null));
        }
        return values;
    }
}
