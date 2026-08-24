package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.persistence.NotificationLogRepository;
import banghak.home.halley.domain.notification.NotificationEventType;
import banghak.home.halley.domain.notification.NotificationLog;
import banghak.home.halley.domain.notification.NotificationStatus;
import tools.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.CHANNEL;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.CREATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.ERROR_MESSAGE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.EVENT_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.PAYLOAD;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.RETRY_COUNT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.SENT_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.STATUS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NotificationLogTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJson;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJsonNode;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class NotificationLogJooqRepository implements NotificationLogRepository {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public NotificationLogJooqRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public NotificationLog save(NotificationLog log) {
        Long id = dsl.insertInto(TABLE)
                .set(EVENT_TYPE, log.eventType() == null ? null : log.eventType().name())
                .set(PROPERTY_ID, log.propertyId())
                .set(CHANNEL, log.channel())
                .set(STATUS, log.status() == null ? null : log.status().name())
                .set(RETRY_COUNT, log.retryCount())
                .set(ERROR_MESSAGE, log.errorMessage())
                .set(PAYLOAD, toJson(log.payload(), objectMapper))
                .set(SENT_AT, toOffset(log.sentAt()))
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<NotificationLog> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<NotificationLog> findAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    @Override
    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    private NotificationLog map(Record r) {
        return new NotificationLog(
                r.get(ID),
                toEnum(NotificationEventType.class, r.get(EVENT_TYPE)),
                r.get(PROPERTY_ID),
                r.get(CHANNEL),
                toEnum(NotificationStatus.class, r.get(STATUS)),
                r.get(RETRY_COUNT),
                r.get(ERROR_MESSAGE),
                toJsonNode(r.get(PAYLOAD), objectMapper),
                toInstant(r.get(CREATED_AT)),
                toInstant(r.get(SENT_AT))
        );
    }
}
