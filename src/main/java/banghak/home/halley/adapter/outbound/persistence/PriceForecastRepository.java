package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastConfidence;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.PriceForecast;
import banghak.home.halley.domain.forecast.PriceOutlook;
import org.jooq.DSLContext;
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
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJson;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJsonNode;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

/**
 * 가격 전망 저장 (설계 I135).
 *
 * <p>매물당 <b>하나만</b> 둡니다 — 이력이 필요하면 그때 별도 테이블을 씁니다.
 * 지금 이력을 쌓으면 쓰지도 않으면서 행만 늘어납니다.
 */
@Repository
public class PriceForecastRepository {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public PriceForecastRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    public PriceForecast upsert(PriceForecast forecast) {
        final PriceOutlook outlook = forecast.outlook();
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, forecast.propertyId())
                .set(DIRECTION, outlook.direction().name())
                .set(CODE_DIRECTION, forecast.codeDirection() == null
                        ? null : forecast.codeDirection().name())
                .set(CONFIDENCE, outlook.confidence().name())
                .set(HORIZON_MONTHS, outlook.horizonMonths())
                .set(FACTORS, toJson(factorsJson(outlook.factors()), objectMapper))
                .set(CAVEATS, toJson(stringsJson(outlook.caveats()), objectMapper))
                .set(MODEL, forecast.model())
                .set(PROMPT_HASH, forecast.promptHash())
                .set(COMPUTED_AT, toOffset(forecast.computedAt()))
                .onConflict(PROPERTY_ID)
                .doUpdate()
                .set(DIRECTION, outlook.direction().name())
                .set(CODE_DIRECTION, forecast.codeDirection() == null
                        ? null : forecast.codeDirection().name())
                .set(CONFIDENCE, outlook.confidence().name())
                .set(HORIZON_MONTHS, outlook.horizonMonths())
                .set(FACTORS, toJson(factorsJson(outlook.factors()), objectMapper))
                .set(CAVEATS, toJson(stringsJson(outlook.caveats()), objectMapper))
                .set(MODEL, forecast.model())
                .set(PROMPT_HASH, forecast.promptHash())
                .set(COMPUTED_AT, toOffset(forecast.computedAt()))
                .execute();
        return findByPropertyId(forecast.propertyId()).orElseThrow();
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
