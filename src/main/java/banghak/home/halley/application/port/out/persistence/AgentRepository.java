package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.property.Agent;

import java.util.List;
import java.util.Optional;

public interface AgentRepository {

    Agent save(Agent agent);

    Optional<Agent> findById(Long id);

    List<Agent> findAll();

    void delete(Long id);
}
