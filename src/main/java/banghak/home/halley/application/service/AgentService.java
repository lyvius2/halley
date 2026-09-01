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
import banghak.home.halley.application.port.out.cache.PropertyDetailCache;
import banghak.home.halley.domain.property.PropertyAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AgentService {

    private final AgentRepository agentRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final PropertyAgentRepository propertyAgentRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyDetailCache detailCache;
    private final ObjectMapper objectMapper;

    public AgentService(PropertyAccessGuard propertyAccessGuard,
                        AgentRepository agentRepository,
                        PropertyAgentRepository propertyAgentRepository,
                        PropertyRepository propertyRepository,
                        PropertyDetailCache detailCache,
                        ObjectMapper objectMapper) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.agentRepository = agentRepository;
        this.propertyAgentRepository = propertyAgentRepository;
        this.propertyRepository = propertyRepository;
        this.detailCache = detailCache;
        this.objectMapper = objectMapper;
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

    /**
     * 중개사 정보를 고치면 <b>그 중개사가 붙은 매물 전부</b>가 낡는다 (설계 I158).
     * 어느 매물인지 되짚는 것보다 통째로 버리는 편이 단순하고, 중개사 수정은 드물다.
     */
    public AgentResponse update(Long id, AgentRequest request) {
        final Agent existing = agentRepository.findById(id).orElseThrow(NotFoundListingsException::new);
        detailCache.evictAll(PropertyDetailCache.AGENTS);
        return toResponse(agentRepository.update(new Agent(
                existing.id(), request.officeName(), request.agentName(), request.phone(), request.mobile(),
                request.registrationNo(), request.address(), request.lat(), request.lng())));
    }

    public void delete(Long id) {
        agentRepository.findById(id).orElseThrow(NotFoundListingsException::new);
        detailCache.evictAll(PropertyDetailCache.AGENTS);
        agentRepository.delete(id);
    }

    @Transactional
    public List<PropertyAgentResponse> linkAgents(Long propertyId, List<PropertyAgentLink> links) {
        propertyAccessGuard.require(propertyId);
        detailCache.evict(PropertyDetailCache.AGENTS, propertyId);
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
        // 붙여넣기로 중개사가 바뀌었을 수 있다 — 매물 것만 버리면 안 되고 전부 버린다
        detailCache.evictAll(PropertyDetailCache.AGENTS);
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

    /**
     * 매물에 붙은 중개사 (설계 I158).
     *
     * <p>중개사마다 `agent` 를 한 번씩 더 읽으므로 왕복이 늘어난다. 거의 안 바뀌는 값이라
     * 캐시를 먼저 본다. <b>접근 검사는 캐시보다 앞에 둔다</b> — 캐시가 있다고 남의 그룹
     * 매물을 보여 주면 안 된다.
     */
    public List<PropertyAgentResponse> propertyAgents(Long propertyId) {
        propertyAccessGuard.require(propertyId);
        final Optional<String> cached = detailCache.get(PropertyDetailCache.AGENTS, propertyId);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(),
                        new TypeReference<List<PropertyAgentResponse>>() {
                        });
            } catch (RuntimeException e) {
                log.warn("Agent cache unreadable - falling back to DB. propertyId={}, cause={}",
                        propertyId, e.getMessage());
                detailCache.evict(PropertyDetailCache.AGENTS, propertyId);
            }
        }
        final List<PropertyAgentResponse> fresh = propertyAgentRepository.findByPropertyId(propertyId).stream()
                .sorted(Comparator.comparing(PropertyAgent::isPrimary).reversed()
                        .thenComparing(PropertyAgent::agentId))
                .map(this::toPropertyAgentResponse)
                .toList();
        detailCache.put(PropertyDetailCache.AGENTS, propertyId, objectMapper.writeValueAsString(fresh));
        return fresh;
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
