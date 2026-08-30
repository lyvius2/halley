package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class PropertyTable {

    private static final String T = "property";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<String> NAME = field(name(T, "name"), String.class);
    public static final Field<String> DONG_HO = field(name(T, "dong_ho"), String.class);
    public static final Field<String> DEAL_TYPE = field(name(T, "deal_type"), String.class);
    public static final Field<Long> PRICE_DEPOSIT = field(name(T, "price_deposit"), Long.class);
    public static final Field<Integer> MAINTENANCE_FEE = field(name(T, "maintenance_fee"), Integer.class);
    public static final Field<String> ADDRESS_ROAD = field(name(T, "address_road"), String.class);
    public static final Field<String> ADDRESS_JIBUN = field(name(T, "address_jibun"), String.class);
    public static final Field<BigDecimal> LAT = field(name(T, "lat"), BigDecimal.class);
    public static final Field<BigDecimal> LNG = field(name(T, "lng"), BigDecimal.class);
    public static final Field<BigDecimal> AREA_SUPPLY_M2 = field(name(T, "area_supply_m2"), BigDecimal.class);
    public static final Field<BigDecimal> AREA_EXCLUSIVE_M2 = field(name(T, "area_exclusive_m2"), BigDecimal.class);
    public static final Field<String> FLOOR_RAW = field(name(T, "floor_raw"), String.class);
    public static final Field<Integer> FLOOR_NO = field(name(T, "floor_no"), Integer.class);
    public static final Field<Integer> FLOOR_TOTAL = field(name(T, "floor_total"), Integer.class);
    public static final Field<String> FLOOR_BAND = field(name(T, "floor_band"), String.class);
    public static final Field<String> ROOM_BATH = field(name(T, "room_bath"), String.class);
    public static final Field<String> DIRECTION = field(name(T, "direction"), String.class);
    public static final Field<Integer> APPROVAL_YEAR = field(name(T, "approval_year"), Integer.class);
    public static final Field<String> MOVE_IN_TYPE = field(name(T, "move_in_type"), String.class);
    public static final Field<Date> MOVE_IN_DATE = field(name(T, "move_in_date"), SQLDataType.DATE);
    public static final Field<BigDecimal> PARKING_PER_HOUSEHOLD = field(name(T, "parking_per_household"), BigDecimal.class);
    public static final Field<Integer> TOTAL_HOUSEHOLDS = field(name(T, "total_households"), Integer.class);
    public static final Field<String> HEATING_TYPE = field(name(T, "heating_type"), String.class);
    public static final Field<Integer> BUILDING_COUNT = field(name(T, "building_count"), Integer.class);
    public static final Field<Long> KB_PRICE = field(name(T, "kb_price"), Long.class);
    public static final Field<Long> BROKERAGE_FEE = field(name(T, "brokerage_fee"), Long.class);
    public static final Field<BigDecimal> BROKERAGE_RATE = field(name(T, "brokerage_rate"), BigDecimal.class);
    public static final Field<Long> ACQUISITION_TAX = field(name(T, "acquisition_tax"), Long.class);
    public static final Field<Long> PROPERTY_TAX = field(name(T, "property_tax"), Long.class);
    public static final Field<String> COMPREHENSIVE_TAX = field(name(T, "comprehensive_tax"), String.class);
    public static final Field<String> SCHOOL_NAME = field(name(T, "school_name"), String.class);
    public static final Field<Integer> SCHOOL_WALK_MINUTES = field(name(T, "school_walk_minutes"), Integer.class);
    public static final Field<String> SCHOOL_SOURCE = field(name(T, "school_source"), String.class);
    public static final Field<String> PNU = field(name(T, "pnu"), String.class);
    public static final Field<Long> OFFICIAL_PRICE = field(name(T, "official_price"), Long.class);
    public static final Field<Integer> OFFICIAL_PRICE_YEAR = field(name(T, "official_price_year"), Integer.class);
    public static final Field<String> SOURCE_TYPE = field(name(T, "source_type"), String.class);
    public static final Field<String> SOURCE_URL = field(name(T, "source_url"), String.class);
    public static final Field<String> NAVER_ARTICLE_NO = field(name(T, "naver_article_no"), String.class);
    public static final Field<String> RAW_PASTE_TEXT = field(name(T, "raw_paste_text"), String.class);
    public static final Field<String> PARSER_VERSION = field(name(T, "parser_version"), String.class);
    public static final Field<JSON> PARSE_CONFIDENCE = field(name(T, "parse_confidence"), JSON.class);
    public static final Field<Boolean> IS_DRAFT = field(name(T, "is_draft"), Boolean.class);
    public static final Field<String> LISTING_STATUS = field(name(T, "listing_status"), String.class);
    public static final Field<Boolean> ACTIVE = field(name(T, "active"), Boolean.class);
    public static final Field<OffsetDateTime> LAST_CHECKED_AT = field(name(T, "last_checked_at"), OffsetDateTime.class);
    public static final Field<Integer> CHECK_FAIL_STREAK = field(name(T, "check_fail_streak"), Integer.class);
    public static final Field<OffsetDateTime> SOLD_DETECTED_AT = field(name(T, "sold_detected_at"), OffsetDateTime.class);
    public static final Field<Long> GROUP_ID = field(name(T, "group_id"), Long.class);
    public static final Field<String> CREATED_BY_NICKNAME =
            field(name(T, "created_by_nickname"), String.class);
    public static final Field<Long> CREATED_BY = field(name(T, "created_by"), Long.class);
    public static final Field<OffsetDateTime> CREATED_AT = field(name(T, "created_at"), OffsetDateTime.class);

    private PropertyTable() {
    }
}
