package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.AgentRequest;
import banghak.home.halley.adapter.inbound.web.dto.AgentResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyAgentLink;
import banghak.home.halley.adapter.inbound.web.dto.PropertyAgentResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.domain.property.DealType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class AgentServiceTest {

    @Autowired
    private AgentService agentService;

    @Autowired
    private PropertyService propertyService;

    @Test
    @DisplayName("중개인을 등록·검색·수정하고 매물에 연결한다")
    void crudAndLink() {
        // given
        final AgentResponse created = agentService.create(request("한빛공인중개", "김중개", "02-123-4567"));
        final PropertyResponse property = propertyService.create(request("중개 테스트"));

        // when
        final List<PropertyAgentResponse> linked = agentService.linkAgents(property.id(),
                List.of(new PropertyAgentLink(created.id(), true)));
        final List<AgentResponse> searched = agentService.list("한빛");

        // then
        assertThat(linked).extracting(PropertyAgentResponse::agentId).contains(created.id());
        assertThat(agentService.propertyAgents(property.id())).hasSize(1);
        assertThat(searched).extracting(AgentResponse::officeName).contains("한빛공인중개");

        // update
        final AgentResponse updated = agentService.update(created.id(), request("새사무소", "김중개", "02-999-9999"));
        assertThat(updated.officeName()).isEqualTo("새사무소");
    }

    private AgentRequest request(String officeName, String agentName, String phone) {
        return new AgentRequest(officeName, agentName, phone, "010-1234-5678", "12345", "서울시", null, null);
    }

    private PropertyRequest request(String name) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null, null,
                "서울시", null, null, null,
                null, null, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
