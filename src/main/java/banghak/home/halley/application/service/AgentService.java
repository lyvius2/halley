package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.AgentRequest;
import banghak.home.halley.adapter.inbound.web.dto.AgentResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyAgentLink;
import banghak.home.halley.adapter.inbound.web.dto.PropertyAgentResponse;
import banghak.home.halley.adapter.outbound.persistence.AgentRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyAgentRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.property.Agent;
import banghak.home.halley.domain.property.PropertyAgent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AgentService {

    private final AgentRepository agentRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final PropertyAgentRepository propertyAgentRepository;
    private final PropertyRepository propertyRepository;

    public AgentService(PropertyAccessGuard propertyAccessGuard,
                                  AgentRepository agentRepository,
                        PropertyAgentRepository propertyAgentRepository,
                        PropertyRepository propertyRepository) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.agentRepository = agentRepository;
        this.propertyAgentRepository = propertyAgentRepository;
        this.propertyRepository = propertyRepository;
    }

    public List<AgentResponse> list(String query) {
        final String q = query == null ? "" : query.trim();
        return agentRepository.findAll().stream()
                .filter(a -> q.isEmpty()
                        || (a.officeName() != null && a.officeName().contains(q))
                        || (a.agentName() != null && a.agentName().contains(q)))
                .map(this::toResponse)
                .toList();
    }

    public AgentResponse create(AgentRequest request) {
        return toResponse(agentRepository.save(new Agent(
                null, request.officeName(), request.agentName(), request.phone(), request.mobile(),
                request.registrationNo(), request.address(), request.lat(), request.lng())));
    }

    public AgentResponse update(Long id, AgentRequest request) {
        final Agent existing = agentRepository.findById(id).orElseThrow(NotFoundListingsException::new);
        return toResponse(agentRepository.update(new Agent(
                existing.id(), request.officeName(), request.agentName(), request.phone(), request.mobile(),
                request.registrationNo(), request.address(), request.lat(), request.lng())));
    }

    public void delete(Long id) {
        agentRepository.findById(id).orElseThrow(NotFoundListingsException::new);
        agentRepository.delete(id);
    }

    @Transactional
    public List<PropertyAgentResponse> linkAgents(Long propertyId, List<PropertyAgentLink> links) {
        propertyAccessGuard.require(propertyId);
        propertyAgentRepository.deleteByPropertyId(propertyId);
        if (links != null) {
            for (final PropertyAgentLink link : links) {
                agentRepository.findById(link.agentId()).orElseThrow(NotFoundListingsException::new);
                propertyAgentRepository.save(new PropertyAgent(propertyId, link.agentId(), link.isPrimary()));
            }
        }
        return propertyAgents(propertyId);
    }

    /**
     * 붙여넣기로 들어온 중개사를 등록·연결한다 (설계 I53).
     * 등록번호가 같으면 기존 중개사를 최신 값으로 갱신하고, 없으면 새로 만든 뒤 대표 중개사로 연결한다.
     */
    @Transactional
    public void upsertFromPaste(Long propertyId, AgentRequest request) {
        if (request == null || isEmpty(request)) {
            return;
        }
        final Agent agent = request.registrationNo() == null || request.registrationNo().isBlank()
                ? agentRepository.save(toAgent(null, request))
                : agentRepository.findByRegistrationNo(request.registrationNo())
                        .map(existing -> agentRepository.update(toAgent(existing.id(), request)))
                        .orElseGet(() -> agentRepository.save(toAgent(null, request)));
        if (propertyAgentRepository.findById(propertyId, agent.id()).isEmpty()) {
            propertyAgentRepository.save(new PropertyAgent(propertyId, agent.id(), true));
        }
    }

    private boolean isEmpty(AgentRequest r) {
        return isBlank(r.officeName()) && isBlank(r.agentName()) && isBlank(r.phone())
                && isBlank(r.mobile()) && isBlank(r.registrationNo()) && isBlank(r.address());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Agent toAgent(Long id, AgentRequest r) {
        return new Agent(id, r.officeName(), r.agentName(), r.phone(), r.mobile(),
                r.registrationNo(), r.address(), r.lat(), r.lng());
    }

    public List<PropertyAgentResponse> propertyAgents(Long propertyId) {
        propertyAccessGuard.require(propertyId);
        return propertyAgentRepository.findByPropertyId(propertyId).stream()
                .sorted(Comparator.comparing(PropertyAgent::isPrimary).reversed()
                        .thenComparing(PropertyAgent::agentId))
                .map(this::toPropertyAgentResponse)
                .toList();
    }

    private PropertyAgentResponse toPropertyAgentResponse(PropertyAgent pa) {
        final Agent agent = agentRepository.findById(pa.agentId()).orElseThrow();
        return new PropertyAgentResponse(
                agent.id(), agent.officeName(), agent.agentName(), agent.phone(), agent.mobile(), pa.isPrimary());
    }

    private AgentResponse toResponse(Agent a) {
        return new AgentResponse(
                a.id(), a.officeName(), a.agentName(), a.phone(), a.mobile(),
                a.registrationNo(), a.address(), a.lat(), a.lng());
    }
}
