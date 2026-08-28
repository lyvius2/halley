package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.Agent;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.AgentTable.ADDRESS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.AgentTable.AGENT_NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.AgentTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.AgentTable.LAT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.AgentTable.LNG;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.AgentTable.MOBILE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.AgentTable.OFFICE_NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.AgentTable.PHONE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.AgentTable.REGISTRATION_NO;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.AgentTable.TABLE;

@Repository
public class AgentRepository {

    private final DSLContext dsl;

    public AgentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Agent save(Agent agent) {
        Long id = dsl.insertInto(TABLE)
                .set(OFFICE_NAME, agent.officeName())
                .set(AGENT_NAME, agent.agentName())
                .set(PHONE, agent.phone())
                .set(MOBILE, agent.mobile())
                .set(REGISTRATION_NO, agent.registrationNo())
                .set(ADDRESS, agent.address())
                .set(LAT, agent.lat())
                .set(LNG, agent.lng())
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<Agent> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public Agent update(Agent agent) {
        dsl.update(TABLE)
                .set(OFFICE_NAME, agent.officeName())
                .set(AGENT_NAME, agent.agentName())
                .set(PHONE, agent.phone())
                .set(MOBILE, agent.mobile())
                .set(REGISTRATION_NO, agent.registrationNo())
                .set(ADDRESS, agent.address())
                .set(LAT, agent.lat())
                .set(LNG, agent.lng())
                .where(ID.eq(agent.id()))
                .execute();
        return findById(agent.id()).orElseThrow();
    }

    /** 등록번호는 중개사무소의 유일 식별자다 — 붙여넣기로 같은 중개사가 반복 등록되는 것을 막는다 (설계 I53). */
    public Optional<Agent> findByRegistrationNo(String registrationNo) {
        return dsl.selectFrom(TABLE)
                .where(REGISTRATION_NO.eq(registrationNo))
                .limit(1)
                .fetchOptional()
                .map(this::map);
    }

    public List<Agent> findAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    private Agent map(Record r) {
        return new Agent(
                r.get(ID),
                r.get(OFFICE_NAME),
                r.get(AGENT_NAME),
                r.get(PHONE),
                r.get(MOBILE),
                r.get(REGISTRATION_NO),
                r.get(ADDRESS),
                r.get(LAT),
                r.get(LNG)
        );
    }
}
