package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.persistence.PropertyAgentRepository;
import banghak.home.halley.domain.property.PropertyAgent;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyAgentTable.AGENT_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyAgentTable.IS_PRIMARY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyAgentTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyAgentTable.TABLE;

@Repository
public class PropertyAgentJooqRepository implements PropertyAgentRepository {

    private final DSLContext dsl;

    public PropertyAgentJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public PropertyAgent save(PropertyAgent propertyAgent) {
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, propertyAgent.propertyId())
                .set(AGENT_ID, propertyAgent.agentId())
                .set(IS_PRIMARY, propertyAgent.isPrimary())
                .execute();
        return findById(propertyAgent.propertyId(), propertyAgent.agentId()).orElseThrow();
    }

    @Override
    public Optional<PropertyAgent> findById(Long propertyId, Long agentId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId).and(AGENT_ID.eq(agentId)))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<PropertyAgent> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .fetch()
                .map(this::map);
    }

    @Override
    public void delete(Long propertyId, Long agentId) {
        dsl.deleteFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId).and(AGENT_ID.eq(agentId)))
                .execute();
    }

    private PropertyAgent map(Record r) {
        return new PropertyAgent(
                r.get(PROPERTY_ID),
                r.get(AGENT_ID),
                Boolean.TRUE.equals(r.get(IS_PRIMARY))
        );
    }
}
