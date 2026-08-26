package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.FloorBand;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.MoveInType;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;
import tools.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ACTIVE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ADDRESS_JIBUN;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ADDRESS_ROAD;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.APPROVAL_YEAR;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.AREA_EXCLUSIVE_M2;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.AREA_SUPPLY_M2;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.BUILDING_COUNT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.CHECK_FAIL_STREAK;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.CREATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.CREATED_BY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.DEAL_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.DIRECTION;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.DONG_HO;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.FLOOR_BAND;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.FLOOR_NO;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.FLOOR_RAW;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.FLOOR_TOTAL;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.HEATING_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.IS_DRAFT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.KB_PRICE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.LAST_CHECKED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.LAT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.LISTING_STATUS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.LNG;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.MAINTENANCE_FEE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.MOVE_IN_DATE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.MOVE_IN_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.NAVER_ARTICLE_NO;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PARKING_PER_HOUSEHOLD;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PARSE_CONFIDENCE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PARSER_VERSION;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PRICE_DEPOSIT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PRICE_MONTHLY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.RAW_PASTE_TEXT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ROOM_BATH;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.SOLD_DETECTED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.SOURCE_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.SOURCE_URL;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.TOTAL_HOUSEHOLDS;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJson;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJsonNode;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toLocalDate;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toSqlDate;

@Repository
public class PropertyRepository {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public PropertyRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    public Property save(Property property) {
        Long id = Objects.requireNonNull(dsl.insertInto(TABLE)
                        .set(NAME, property.name())
                        .set(DONG_HO, property.dongHo())
                        .set(DEAL_TYPE, property.dealType() == null ? null : property.dealType().name())
                        .set(PRICE_DEPOSIT, property.priceDeposit())
                        .set(PRICE_MONTHLY, property.priceMonthly())
                        .set(MAINTENANCE_FEE, property.maintenanceFee())
                        .set(ADDRESS_ROAD, property.addressRoad())
                        .set(ADDRESS_JIBUN, property.addressJibun())
                        .set(LAT, property.lat())
                        .set(LNG, property.lng())
                        .set(AREA_SUPPLY_M2, property.areaSupplyM2())
                        .set(AREA_EXCLUSIVE_M2, property.areaExclusiveM2())
                        .set(FLOOR_RAW, property.floorRaw())
                        .set(FLOOR_NO, property.floorNo())
                        .set(FLOOR_TOTAL, property.floorTotal())
                        .set(FLOOR_BAND, property.floorBand() == null ? null : property.floorBand().name())
                        .set(ROOM_BATH, property.roomBath())
                        .set(DIRECTION, property.direction())
                        .set(APPROVAL_YEAR, property.approvalYear())
                        .set(MOVE_IN_TYPE, property.moveInType() == null ? null : property.moveInType().name())
                        .set(MOVE_IN_DATE, toSqlDate(property.moveInDate()))
                        .set(PARKING_PER_HOUSEHOLD, property.parkingPerHousehold())
                        .set(TOTAL_HOUSEHOLDS, property.totalHouseholds())
                        .set(HEATING_TYPE, property.heatingType())
                        .set(BUILDING_COUNT, property.buildingCount())
                        .set(KB_PRICE, property.kbPrice())
                        .set(SOURCE_TYPE, property.sourceType() == null ? null : property.sourceType().name())
                        .set(SOURCE_URL, property.sourceUrl())
                        .set(NAVER_ARTICLE_NO, property.naverArticleNo())
                        .set(RAW_PASTE_TEXT, property.rawPasteText())
                        .set(PARSER_VERSION, property.parserVersion())
                        .set(PARSE_CONFIDENCE, toJson(property.parseConfidence(), objectMapper))
                        .set(IS_DRAFT, property.isDraft())
                        .set(LISTING_STATUS, property.listingStatus() == null ? null : property.listingStatus().name())
                        .set(ACTIVE, property.active())
                        .set(LAST_CHECKED_AT, toOffset(property.lastCheckedAt()))
                        .set(CHECK_FAIL_STREAK, property.checkFailStreak())
                        .set(SOLD_DETECTED_AT, toOffset(property.soldDetectedAt()))
                        .set(CREATED_BY, property.createdBy())
                        .returningResult(ID)
                        .fetchOne())
                .component1();
        return findById(id).orElseThrow();
    }

    public Property update(Property property) {
        dsl.update(TABLE)
                .set(NAME, property.name())
                .set(DONG_HO, property.dongHo())
                .set(DEAL_TYPE, property.dealType() == null ? null : property.dealType().name())
                .set(PRICE_DEPOSIT, property.priceDeposit())
                .set(PRICE_MONTHLY, property.priceMonthly())
                .set(MAINTENANCE_FEE, property.maintenanceFee())
                .set(ADDRESS_ROAD, property.addressRoad())
                .set(ADDRESS_JIBUN, property.addressJibun())
                .set(LAT, property.lat())
                .set(LNG, property.lng())
                .set(AREA_SUPPLY_M2, property.areaSupplyM2())
                .set(AREA_EXCLUSIVE_M2, property.areaExclusiveM2())
                .set(FLOOR_RAW, property.floorRaw())
                .set(FLOOR_NO, property.floorNo())
                .set(FLOOR_TOTAL, property.floorTotal())
                .set(FLOOR_BAND, property.floorBand() == null ? null : property.floorBand().name())
                .set(ROOM_BATH, property.roomBath())
                .set(DIRECTION, property.direction())
                .set(APPROVAL_YEAR, property.approvalYear())
                .set(MOVE_IN_TYPE, property.moveInType() == null ? null : property.moveInType().name())
                .set(MOVE_IN_DATE, toSqlDate(property.moveInDate()))
                .set(PARKING_PER_HOUSEHOLD, property.parkingPerHousehold())
                .set(TOTAL_HOUSEHOLDS, property.totalHouseholds())
                .set(HEATING_TYPE, property.heatingType())
                .set(BUILDING_COUNT, property.buildingCount())
                .set(KB_PRICE, property.kbPrice())
                .set(SOURCE_TYPE, property.sourceType() == null ? null : property.sourceType().name())
                .set(SOURCE_URL, property.sourceUrl())
                .set(NAVER_ARTICLE_NO, property.naverArticleNo())
                .set(RAW_PASTE_TEXT, property.rawPasteText())
                .set(PARSER_VERSION, property.parserVersion())
                .set(PARSE_CONFIDENCE, toJson(property.parseConfidence(), objectMapper))
                .set(IS_DRAFT, property.isDraft())
                .set(LISTING_STATUS, property.listingStatus() == null ? null : property.listingStatus().name())
                .set(ACTIVE, property.active())
                .set(LAST_CHECKED_AT, toOffset(property.lastCheckedAt()))
                .set(CHECK_FAIL_STREAK, property.checkFailStreak())
                .set(SOLD_DETECTED_AT, toOffset(property.soldDetectedAt()))
                .set(CREATED_BY, property.createdBy())
                .where(ID.eq(property.id()))
                .execute();
        return findById(property.id()).orElseThrow();
    }

    public Optional<Property> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public List<Property> findAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    public List<Property> findByDealType(DealType dealType) {
        return dsl.selectFrom(TABLE)
                .where(DEAL_TYPE.eq(dealType.name()))
                .fetch()
                .map(this::map);
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    private Property map(Record r) {
        return new Property(
                r.get(ID),
                r.get(NAME),
                r.get(DONG_HO),
                toEnum(DealType.class, r.get(DEAL_TYPE)),
                r.get(PRICE_DEPOSIT),
                r.get(PRICE_MONTHLY),
                r.get(MAINTENANCE_FEE),
                r.get(ADDRESS_ROAD),
                r.get(ADDRESS_JIBUN),
                r.get(LAT),
                r.get(LNG),
                r.get(AREA_SUPPLY_M2),
                r.get(AREA_EXCLUSIVE_M2),
                r.get(FLOOR_RAW),
                r.get(FLOOR_NO),
                r.get(FLOOR_TOTAL),
                toEnum(FloorBand.class, r.get(FLOOR_BAND)),
                r.get(ROOM_BATH),
                r.get(DIRECTION),
                r.get(APPROVAL_YEAR),
                toEnum(MoveInType.class, r.get(MOVE_IN_TYPE)),
                toLocalDate(r.get(MOVE_IN_DATE)),
                r.get(PARKING_PER_HOUSEHOLD),
                r.get(TOTAL_HOUSEHOLDS),
                r.get(HEATING_TYPE),
                r.get(BUILDING_COUNT),
                r.get(KB_PRICE),
                toEnum(SourceType.class, r.get(SOURCE_TYPE)),
                r.get(SOURCE_URL),
                r.get(NAVER_ARTICLE_NO),
                r.get(RAW_PASTE_TEXT),
                r.get(PARSER_VERSION),
                toJsonNode(r.get(PARSE_CONFIDENCE), objectMapper),
                Boolean.TRUE.equals(r.get(IS_DRAFT)),
                toEnum(ListingStatus.class, r.get(LISTING_STATUS)),
                Boolean.TRUE.equals(r.get(ACTIVE)),
                toInstant(r.get(LAST_CHECKED_AT)),
                r.get(CHECK_FAIL_STREAK),
                toInstant(r.get(SOLD_DETECTED_AT)),
                r.get(CREATED_BY),
                toInstant(r.get(CREATED_AT))
        );
    }
}
