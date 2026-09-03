package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.FloorBand;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.MoveInType;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SchoolSource;
import banghak.home.halley.domain.property.SourceType;
import tools.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ACQUISITION_TAX;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ACTIVE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ADDRESS_JIBUN;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ADDRESS_ROAD;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.APPROVAL_YEAR;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.AREA_EXCLUSIVE_M2;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.AREA_SUPPLY_M2;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.BROKERAGE_FEE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.BROKERAGE_RATE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.BUILDING_COUNT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.CHECK_FAIL_STREAK;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.COMPREHENSIVE_TAX;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.CREATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.GROUP_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.CREATED_BY_NICKNAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.CREATED_BY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.DEAL_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.DIRECTION;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.DONG_HO;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.COMPLEX_ID;
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
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.OFFICIAL_PRICE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.OFFICIAL_PRICE_YEAR;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PNU;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PARSER_VERSION;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PARSE_CONFIDENCE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PARSE_CONFIDENCE_RAW;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PRICE_DEPOSIT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.PROPERTY_TAX;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.RAW_PASTE_TEXT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ROOM_BATH;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.SCHOOL_NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.SCHOOL_SOURCE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.SCHOOL_WALK_MINUTES;
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
                        .set(BROKERAGE_FEE, property.brokerageFee())
                        .set(BROKERAGE_RATE, property.brokerageRate())
                        .set(ACQUISITION_TAX, property.acquisitionTax())
                        .set(PROPERTY_TAX, property.propertyTax())
                        .set(COMPREHENSIVE_TAX, property.comprehensiveTax())
                        .set(SCHOOL_NAME, property.schoolName())
                        .set(SCHOOL_WALK_MINUTES, property.schoolWalkMinutes())
                        .set(SCHOOL_SOURCE, property.schoolSource() == null ? null : property.schoolSource().name())
                        .set(PNU, property.pnu())
                        .set(OFFICIAL_PRICE, property.officialPrice())
                        .set(OFFICIAL_PRICE_YEAR, property.officialPriceYear())
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
                        .set(GROUP_ID, property.groupId())
                        .set(CREATED_BY_NICKNAME, property.createdByNickname())
                        .set(CREATED_BY, property.createdBy())
                        .returningResult(ID)
                        .fetchOne())
                .component1();
        return findById(id).orElseThrow();
    }

    /**
     * 단지 번호만 따로 적는다 (설계 I266).
     *
     * <p>{@code Property} 레코드에 칸을 더하지 않기로 했으므로(55칸) 이 값만
     * 따로 씁니다. 매물의 다른 값은 건드리지 않습니다.
     */
    public void setComplexId(Long propertyId, Long complexId) {
        dsl.update(TABLE)
                .set(COMPLEX_ID, complexId)
                .where(ID.eq(propertyId))
                .execute();
    }

    /**
     * 좌표만 따로 적는다 (설계 I268).
     *
     * <p>동을 가려 받은 건물 좌표로 바꿔 줄 때 씁니다. 매물의 다른 값은
     * 건드리지 않습니다 — 사람이 고친 값을 덮으면 안 됩니다.
     */
    public void setCoordinates(Long propertyId, BigDecimal lat, BigDecimal lng) {
        dsl.update(TABLE)
                .set(LAT, lat)
                .set(LNG, lng)
                .where(ID.eq(propertyId))
                .execute();
    }

    public Property update(Property property) {
        dsl.update(TABLE)
                .set(NAME, property.name())
                .set(DONG_HO, property.dongHo())
                .set(DEAL_TYPE, property.dealType() == null ? null : property.dealType().name())
                .set(PRICE_DEPOSIT, property.priceDeposit())
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
                .set(BROKERAGE_FEE, property.brokerageFee())
                .set(BROKERAGE_RATE, property.brokerageRate())
                .set(ACQUISITION_TAX, property.acquisitionTax())
                .set(PROPERTY_TAX, property.propertyTax())
                .set(COMPREHENSIVE_TAX, property.comprehensiveTax())
                .set(SCHOOL_NAME, property.schoolName())
                .set(SCHOOL_WALK_MINUTES, property.schoolWalkMinutes())
                .set(SCHOOL_SOURCE, property.schoolSource() == null ? null : property.schoolSource().name())
                .set(PNU, property.pnu())
                .set(OFFICIAL_PRICE, property.officialPrice())
                .set(OFFICIAL_PRICE_YEAR, property.officialPriceYear())
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
                .set(GROUP_ID, property.groupId())
                        .set(CREATED_BY_NICKNAME, property.createdByNickname())
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

    /** 채점 버전 확인용 — 전체 레코드를 읽지 않는다 (설계 I85). */
    public List<Long> findAllIds() {
        return dsl.select(ID).from(TABLE).orderBy(ID.asc()).fetch(ID);
    }

    /** 한 그룹의 매물만 (설계 I87). 회원은 자기 그룹 밖을 볼 수 없다. */
    public List<Property> findByGroupId(Long groupId) {
        return dsl.selectFrom(TABLE)
                .where(GROUP_ID.eq(groupId))
                .orderBy(ID.desc())
                .fetch()
                .map(this::map);
    }

    public List<Property> findByGroupIdAndDealType(Long groupId, DealType dealType) {
        return dsl.selectFrom(TABLE)
                .where(GROUP_ID.eq(groupId))
                .and(DEAL_TYPE.eq(dealType.name()))
                .orderBy(ID.desc())
                .fetch()
                .map(this::map);
    }

    /** 그룹이 사라질 때 그 그룹의 매물도 함께 지운다 (설계 I87 · 규칙 4). */
    /** 탈퇴 직전 등록자 이름을 값으로 굳힌다 (설계 I88). */
    public int snapshotCreatorNickname(Long userId, String nickname) {
        return dsl.update(TABLE)
                .set(CREATED_BY_NICKNAME, nickname)
                .where(CREATED_BY.eq(userId))
                .execute();
    }

    public void deleteByGroupId(Long groupId) {
        dsl.deleteFrom(TABLE).where(GROUP_ID.eq(groupId)).execute();
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

    public List<Property> findBatchTargets() {
        return dsl.selectFrom(TABLE)
                .where(SOURCE_URL.isNotNull())
                .and(LISTING_STATUS.in("ACTIVE", "UNREACHABLE"))
                .and(IS_DRAFT.eq(false))
                .fetch()
                .map(this::map);
    }

    public List<Property> findRecentSoldOut(int limit) {
        return dsl.selectFrom(TABLE)
                .where(LISTING_STATUS.eq("SOLD_OUT").and(ACTIVE.eq(false)))
                .orderBy(SOLD_DETECTED_AT.desc().nullsLast())
                .limit(limit)
                .fetch()
                .map(this::map);
    }

    public void updateListingStatus(Long id, ListingStatus status, boolean active,
                                    Integer checkFailStreak, Instant soldDetectedAt) {
        dsl.update(TABLE)
                .set(LISTING_STATUS, status.name())
                .set(ACTIVE, active)
                .set(CHECK_FAIL_STREAK, checkFailStreak)
                .set(SOLD_DETECTED_AT, toOffset(soldDetectedAt))
                .where(ID.eq(id))
                .execute();
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
                r.get(BROKERAGE_FEE),
                r.get(BROKERAGE_RATE),
                r.get(ACQUISITION_TAX),
                r.get(PROPERTY_TAX),
                r.get(COMPREHENSIVE_TAX),
                r.get(SCHOOL_NAME),
                r.get(SCHOOL_WALK_MINUTES),
                toEnum(SchoolSource.class, r.get(SCHOOL_SOURCE)),
                r.get(PNU),
                r.get(OFFICIAL_PRICE),
                r.get(OFFICIAL_PRICE_YEAR),
                toEnum(SourceType.class, r.get(SOURCE_TYPE)),
                r.get(SOURCE_URL),
                r.get(NAVER_ARTICLE_NO),
                r.get(RAW_PASTE_TEXT),
                r.get(PARSER_VERSION),
                toJsonNode(r.get(PARSE_CONFIDENCE_RAW), objectMapper),
                Boolean.TRUE.equals(r.get(IS_DRAFT)),
                toEnum(ListingStatus.class, r.get(LISTING_STATUS)),
                Boolean.TRUE.equals(r.get(ACTIVE)),
                toInstant(r.get(LAST_CHECKED_AT)),
                r.get(CHECK_FAIL_STREAK),
                toInstant(r.get(SOLD_DETECTED_AT)),
                r.get(GROUP_ID),
                r.get(CREATED_BY_NICKNAME),
                r.get(CREATED_BY),
                toInstant(r.get(CREATED_AT))
        );
    }
}
