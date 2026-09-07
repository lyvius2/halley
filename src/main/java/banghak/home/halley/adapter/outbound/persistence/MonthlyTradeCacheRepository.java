package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.reference.CachedDealType;
import banghak.home.halley.domain.reference.MonthlyTrades;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.MonthlyTradeCacheTable.DEAL_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.MonthlyTradeCacheTable.DEAL_YM;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.MonthlyTradeCacheTable.FETCHED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.MonthlyTradeCacheTable.LAWD_CD;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.MonthlyTradeCacheTable.PAYLOAD;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.MonthlyTradeCacheTable.PAYLOAD_RAW;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.MonthlyTradeCacheTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.MonthlyTradeCacheTable.TRADE_COUNT;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJson;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJsonNode;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

/** 법정동·월별 실거래 원본 캐시. */
@Slf4j
@Repository
public class MonthlyTradeCacheRepository {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public MonthlyTradeCacheRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

 /** 한 달치를 넣거나 갈아 끼운다. */
    public void upsert(MonthlyTrades monthly) {
        final String ym = monthly.dealYm().format(YM);
        dsl.insertInto(TABLE)
                .set(LAWD_CD, monthly.lawdCd())
                .set(DEAL_YM, ym)
                .set(DEAL_TYPE, monthly.dealType().name())
                .set(PAYLOAD, toJson(toJsonArray(monthly.trades()), objectMapper))
                .set(TRADE_COUNT, monthly.count())
                .set(FETCHED_AT, toOffset(monthly.fetchedAt()))
                .onConflict(LAWD_CD, DEAL_YM, DEAL_TYPE)
                .doUpdate()
                .set(PAYLOAD, toJson(toJsonArray(monthly.trades()), objectMapper))
                .set(TRADE_COUNT, monthly.count())
                .set(FETCHED_AT, toOffset(monthly.fetchedAt()))
                .execute();
    }

    public Optional<MonthlyTrades> find(String lawdCd, YearMonth dealYm, CachedDealType dealType) {
        return dsl.selectFrom(TABLE)
                .where(LAWD_CD.eq(lawdCd), DEAL_YM.eq(dealYm.format(YM)), DEAL_TYPE.eq(dealType.name()))
                .fetchOptional()
                .map(this::map);
    }

 /** 여러 달을 한 번에. */
    public Map<YearMonth, MonthlyTrades> findAll(String lawdCd, Collection<YearMonth> months,
                                                 CachedDealType dealType) {
        if (lawdCd == null || months == null || months.isEmpty()) {
            return Map.of();
        }
        final List<String> keys = months.stream().map(m -> m.format(YM)).toList();
        return dsl.selectFrom(TABLE)
                .where(LAWD_CD.eq(lawdCd), DEAL_YM.in(keys), DEAL_TYPE.eq(dealType.name()))
                .fetch()
                .map(this::map)
                .stream()
                .collect(Collectors.toMap(MonthlyTrades::dealYm, m -> m, (a, b) -> a));
    }

    private MonthlyTrades map(Record r) {
        return new MonthlyTrades(
                r.get(LAWD_CD),
                YearMonth.parse(r.get(DEAL_YM), YM),
                toEnum(CachedDealType.class, r.get(DEAL_TYPE)),
                toTrades(toJsonNode(r.get(PAYLOAD_RAW), objectMapper)),
                toInstant(r.get(FETCHED_AT)));
    }

 /** 도메인을 JSON으로. 필드 이름을 짧게 두지 않습니다. 나중에 이 캐시를 사람이 */
    private JsonNode toJsonArray(List<ReferenceTrade> trades) {
        final ArrayNode array = objectMapper.createArrayNode();
        if (trades == null) {
            return array;
        }
        for (final ReferenceTrade trade : trades) {
            final ObjectNode node = array.addObject();
            node.put("apartmentName", trade.apartmentName());
            node.put("dealAmount", trade.dealAmount());
            node.put("areaM2", trade.areaM2());
            node.put("floorNo", trade.floorNo());
            node.put("contractDate", trade.contractDate() == null ? null : trade.contractDate().toString());
        }
        return array;
    }

    private List<ReferenceTrade> toTrades(JsonNode array) {
        final List<ReferenceTrade> trades = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return trades;
        }
        for (final JsonNode node : array) {
            trades.add(new ReferenceTrade(
                    node.path("apartmentName").asString(null),
                    node.path("dealAmount").isNull() ? null : node.path("dealAmount").asLong(),
                    node.path("areaM2").isNull() || node.path("areaM2").isMissingNode()
                            ? null : new BigDecimal(node.path("areaM2").asString()),
                    node.path("floorNo").isNull() ? null : node.path("floorNo").asInt(),
                    node.path("contractDate").isNull() || node.path("contractDate").isMissingNode()
                            ? null : LocalDate.parse(node.path("contractDate").asString())));
        }
        return trades;
    }
}
