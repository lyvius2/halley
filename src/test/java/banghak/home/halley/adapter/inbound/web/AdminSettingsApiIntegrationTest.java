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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminSettingsApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("관리자가 설정을 조회·수정·알림 이력을 조회한다")
    void adminSettingsFlow() throws Exception {
        // given
        userService.create(new CreateUserRequest(
                "settings", "settings-admin", "password1!", UserRole.ADMIN,
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

        // when / then
        mockMvc.perform(get("/api/admin/settings").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].configKey").isNotEmpty());

        mockMvc.perform(put("/api/admin/settings").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"configKey":"batch.listingCheck.cron","configValue":"0 0 8 * * *"}]
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/notifications").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비관리자는 관리자 설정 API에 접근할 수 없다")
    void memberForbidden() throws Exception {
        // given
        userService.create(new CreateUserRequest(
                "settings-m", "settings-member", "password1!", UserRole.MEMBER,
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

        // when
        mockMvc.perform(get("/api/admin/settings").session(session))

                // then
                .andExpect(status().isForbidden());
    }
}
