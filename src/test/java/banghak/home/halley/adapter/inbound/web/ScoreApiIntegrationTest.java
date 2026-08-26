package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.application.service.UserService;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ScoreApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("인증 사용자가 쾌적함 점수를 매기면 재채점되어 COMFORT에 반영된다")
    void comfortScoreReflected() throws Exception {
        // given
        userService.create(new CreateUserRequest(
                "score-user", "score@example.com", "password1!", UserRole.MEMBER, null, null, null, 0L));
        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"score@example.com\",\"password\":\"password1!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1!\",\"newPassword\":\"newpassword2!\"}"))
                .andExpect(status().isNoContent());

        final String created = mockMvc.perform(post("/api/properties").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"점수 테스트\",\"dealType\":\"SALE\",\"priceDeposit\":300000000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        final String id = objectMapper.readTree(created).get("property").get("id").asString();

        // when
        final String body = mockMvc.perform(put("/api/properties/" + id + "/scores").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scores\":{\"COMFORT\":5}}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        double comfortEffective = -1.0;
        for (final JsonNode s : objectMapper.readTree(body).path("scores")) {
            if ("COMFORT".equals(s.path("code").asString())) {
                comfortEffective = s.path("effectiveScore").asDouble();
            }
        }
        assertThat(comfortEffective).isEqualTo(100.0);
    }
}
