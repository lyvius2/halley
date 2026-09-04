package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.application.port.out.external.ClaudeModelsPort;
import banghak.home.halley.application.service.UserService;
import banghak.home.halley.domain.llm.LlmModelOption;
import banghak.home.halley.domain.user.UserRole;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminSettingsApiIntegrationTest {

    /** 목록은 Anthropic 에서 온다 — 시험에서는 고정한다 (설계 I267). */
    @TestConfiguration
    static class Models {

        @Bean
        @Primary
        ClaudeModelsPort claudeModelsPort() {
            return () -> List.of(
                    LlmModelOption.of("claude-opus-5", "Claude Opus 5"),
                    LlmModelOption.of("claude-sonnet-5", "Claude Sonnet 5"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("관리자가 설정을 조회·수정·알림 이력을 조회한다")
    void adminSettingsFlow() throws Exception {
        // given
        userService.create(new CreateUserRequest(
                "settings", "settings-admin", null, "password1!", UserRole.ADMIN,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L));
        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"settings\",\"password\":\"password1!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1!\",\"newPassword\":\"newpassword2!\"}"))
                .andExpect(status().isNoContent());

        // 프로필을 확인해야 나머지 API가 열린다 (설계 I100 · I105)
        mockMvc.perform(put("/api/users/me/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workplaceName":"회사","workplaceLat":37.5,"workplaceLng":127.0,
                                 "availableBudget":300000000,"annualIncome":60000000,"existingLoan":0}
                                """))
                .andExpect(status().isOk());

        // when / then
        mockMvc.perform(get("/api/admin/settings").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].configKey").isNotEmpty());

        mockMvc.perform(put("/api/admin/settings").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"configKey":"loan.regulation.profile","configValue":"2025-10-15"}]
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/notifications").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("고른 모델은 저장되고 설정을 다시 열어도 그대로 돌아온다 (설계 I280)")
    void chosenModelSurvivesAReopen() throws Exception {
        // given
        userService.create(new CreateUserRequest(
                "llm-admin", "llm-admin", null, "password1!", UserRole.ADMIN,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L));
        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"llm-admin\",\"password\":\"password1!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1!\",\"newPassword\":\"newpassword2!\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/users/me/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workplaceName":"회사","workplaceLat":37.5,"workplaceLng":127.0,
                                 "availableBudget":300000000,"annualIncome":60000000,"existingLoan":0}
                                """))
                .andExpect(status().isOk());

        // when — 화면이 하는 그대로: 고른 값을 PUT 한다
        mockMvc.perform(put("/api/admin/llm-models").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"key":"llm.model.recommendation","model":"claude-sonnet-5"}]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[?(@.key=='llm.model.recommendation')].model")
                        .value("claude-sonnet-5"));

        // then — 모달을 다시 연 셈 치고 새로 읽어도 고른 값이 온다
        mockMvc.perform(get("/api/admin/llm-models").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[?(@.key=='llm.model.recommendation')].model")
                        .value("claude-sonnet-5"))
                .andExpect(jsonPath("$.models[?(@.id=='claude-sonnet-5')]").exists());
    }

    @Test
    @DisplayName("비관리자는 관리자 설정 API에 접근할 수 없다")
    void memberForbidden() throws Exception {
        // given
        userService.create(new CreateUserRequest(
                "settings-m", "settings-member", null, "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L));
        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"settings-m\",\"password\":\"password1!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1!\",\"newPassword\":\"newpassword2!\"}"))
                .andExpect(status().isNoContent());

        // 프로필을 확인해야 나머지 API가 열린다 (설계 I100 · I105)
        mockMvc.perform(put("/api/users/me/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workplaceName":"회사","workplaceLat":37.5,"workplaceLng":127.0,
                                 "availableBudget":300000000,"annualIncome":60000000,"existingLoan":0}
                                """))
                .andExpect(status().isOk());

        // when
        mockMvc.perform(get("/api/admin/settings").session(session))

                // then
                .andExpect(status().isForbidden());
    }
}
