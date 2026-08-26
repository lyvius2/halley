package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.scoring.CommuteResult;
import tools.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.CommuteResultTable.FETCHED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CommuteResultTable.PATH_SUMMARY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CommuteResultTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CommuteResultTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CommuteResultTable.TOTAL_MINUTES;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CommuteResultTable.TRANSFER_COUNT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CommuteResultTable.USER_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CommuteResultTable.WALK_MINUTES;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJson;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJsonNode;

@Repository
public class CommuteResultRepository {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public CommuteResultRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    public CommuteResult save(CommuteResult commuteResult) {
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, commuteResult.propertyId())
                .set(USER_ID, commuteResult.userId())
                .set(TOTAL_MINUTES, commuteResult.totalMinutes())
                .set(TRANSFER_COUNT, commuteResult.transferCount())
                .set(WALK_MINUTES, commuteResult.walkMinutes())
                .set(PATH_SUMMARY, toJson(commuteResult.pathSummary(), objectMapper))
                .execute();
        return findById(commuteResult.propertyId(), commuteResult.userId()).orElseThrow();
    }

    public void upsert(CommuteResult commuteResult) {
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, commuteResult.propertyId())
                .set(USER_ID, commuteResult.userId())
                .set(TOTAL_MINUTES, commuteResult.totalMinutes())
                .set(TRANSFER_COUNT, commuteResult.transferCount())
                .set(WALK_MINUTES, commuteResult.walkMinutes())
                .set(PATH_SUMMARY, toJson(commuteResult.pathSummary(), objectMapper))
                .onConflict(PROPERTY_ID, USER_ID)
                .doUpdate()
                .set(TOTAL_MINUTES, commuteResult.totalMinutes())
                .set(TRANSFER_COUNT, commuteResult.transferCount())
                .set(WALK_MINUTES, commuteResult.walkMinutes())
                .set(PATH_SUMMARY, toJson(commuteResult.pathSummary(), objectMapper))
                .execute();
    }

    public Optional<CommuteResult> findById(Long propertyId, Long userId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId).and(USER_ID.eq(userId)))
                .fetchOptional()
                .map(this::map);
    }

    public List<CommuteResult> findByUserId(Long userId) {
        return dsl.selectFrom(TABLE)
                .where(USER_ID.eq(userId))
                .fetch()
                .map(this::map);
    }

    public void delete(Long propertyId, Long userId) {
        dsl.deleteFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId).and(USER_ID.eq(userId)))
                .execute();
    }

    private CommuteResult map(Record r) {
        return new CommuteResult(
                r.get(PROPERTY_ID),
                r.get(USER_ID),
                r.get(TOTAL_MINUTES),
                r.get(TRANSFER_COUNT),
                r.get(WALK_MINUTES),
                toJsonNode(r.get(PATH_SUMMARY), objectMapper),
                toInstant(r.get(FETCHED_AT))
        );
    }
}
