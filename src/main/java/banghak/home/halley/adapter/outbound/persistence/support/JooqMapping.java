package banghak.home.halley.adapter.outbound.persistence.support;

import org.jooq.JSON;
import org.jooq.JSONB;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class JooqMapping {

    private JooqMapping() {
    }

    public static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.toInstant();
    }

    public static <E extends Enum<E>> E toEnum(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    public static java.sql.Date toSqlDate(java.time.LocalDate localDate) {
        return localDate == null ? null : java.sql.Date.valueOf(localDate);
    }

    public static java.time.LocalDate toLocalDate(java.sql.Date date) {
        return date == null ? null : date.toLocalDate();
    }

    public static java.sql.Time toSqlTime(java.time.LocalTime localTime) {
        return localTime == null ? null : java.sql.Time.valueOf(localTime);
    }

    public static java.time.LocalTime toLocalTime(java.sql.Time time) {
        return time == null ? null : time.toLocalTime();
    }

    public static JSON toJson(JsonNode node, ObjectMapper mapper) {
        return node == null ? null : JSON.valueOf(mapper.writeValueAsString(node));
    }

    /**
     * JSON 칸을 읽는다 (설계 I117).
     *
     * <p><b>타입을 가리지 않습니다.</b> live(PostgreSQL)는 {@code JSONB}, local(H2)은 {@code JSON},
     * 드라이버에 따라 {@code String}이나 {@code PGobject}로 오기도 합니다. 한 쪽으로 못 박으면
     * 다른 쪽에서 {@code ClassCastException}이 나는데, <b>로컬에서는 재현되지 않습니다.</b>
     */
    public static JsonNode toJsonNode(Object json, ObjectMapper mapper) {
        final String raw = switch (json) {
            case null -> null;
            case JSON j -> j.data();
            case JSONB j -> j.data();
            case String s -> s;
            default -> json.toString();
        };
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return mapper.readTree(raw);
    }
}
