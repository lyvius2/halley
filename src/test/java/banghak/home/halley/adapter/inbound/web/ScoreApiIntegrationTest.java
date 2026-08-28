package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.application.service.UserService;
import banghak.home.halley.domain.user.UserRole;

import java.math.BigDecimal;
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
                "score", "score-user", "score@example.com", "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L));
        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"score\",\"password\":\"password1!\"}"))
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

    @Test
    @DisplayName("재채점 트리거는 매물을 수정하지 않고 점수를 다시 계산해 반환한다")
    void rescoreRecomputesScores() throws Exception {
        // given
        final MockHttpSession session = login("rescore-user", "rescore@example.com");
        final String created = mockMvc.perform(post("/api/properties").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"재채점 테스트\",\"dealType\":\"SALE\",\"priceDeposit\":300000000,\"floorNo\":9}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        final String id = objectMapper.readTree(created).get("property").get("id").asString();

        // when
        final String body = mockMvc.perform(post("/api/properties/" + id + "/rescore").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then — 매물은 그대로이고 채점 결과가 다시 계산되어 온다
        final JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("property").path("id").asString()).isEqualTo(id);
        assertThat(response.path("property").path("name").asString()).isEqualTo("재채점 테스트");
        double floorScore = -1.0;
        for (final JsonNode s : response.path("scores")) {
            if ("FLOOR".equals(s.path("code").asString())) {
                floorScore = s.path("effectiveScore").asDouble();
            }
        }
        assertThat(floorScore).isEqualTo(100.0);
    }

    @Test
    @DisplayName("없는 매물을 재채점하면 404를 반환한다")
    void rescoreUnknownProperty() throws Exception {
        // given
        final MockHttpSession session = login("rescore-404", "rescore404@example.com");

        // when · then
        mockMvc.perform(post("/api/properties/999999/rescore").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비인증 요청은 재채점할 수 없다")
    void rescoreRequiresAuth() throws Exception {
        // when · then
        mockMvc.perform(post("/api/properties/1/rescore"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String nickname, String email) throws Exception {
        userService.create(new CreateUserRequest(
                email.split("@")[0], nickname, email, "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L));
        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + email.split("@")[0] + "\",\"password\":\"password1!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1!\",\"newPassword\":\"newpassword2!\"}"))
                .andExpect(status().isNoContent());
        return session;
    }
}
