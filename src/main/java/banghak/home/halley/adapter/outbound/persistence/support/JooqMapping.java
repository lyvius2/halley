package banghak.home.halley.adapter.outbound.persistence.support;

import org.jooq.JSON;
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

    public static JsonNode toJsonNode(JSON json, ObjectMapper mapper) {
        return json == null ? null : mapper.readTree(json.data());
    }
}
