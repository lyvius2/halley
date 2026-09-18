package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.application.service.UserService;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청 DTO 검증 (설계 I299).
 *
 * <p>Bean Validation 이 아예 없었다. 길이·형식 제한 없이 서비스까지 내려갔다.
 * 어긋난 칸 이름은 말하되 보낸 값은 되돌려 주지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "membership.sign-up.open=true")
@DisplayName("요청 검증 (설계 I299)")
class RequestValidationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;

    @Test
    @DisplayName("로그인 ID 가 비어 있으면 400 — 인증까지 가지 않는다")
    void rejectsBlankLoginId() throws Exception {
        // when / then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"  \",\"password\":\"password1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("loginId")));
    }

    @Test
    @DisplayName("가입 비밀번호가 8자 미만이면 400")
    void rejectsShortSignUpPassword() throws Exception {
        // when / then
        mockMvc.perform(post("/api/users/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"newbie1\",\"nickname\":\"신입\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("password")));
    }

    @Test
    @DisplayName("매물명이 200자를 넘으면 400")
    void rejectsOverlongPropertyName() throws Exception {
        // given
        final MockHttpSession session = login("validator1");
        final String longName = "가".repeat(201);

        // when / then
        mockMvc.perform(post("/api/properties").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\",\"dealType\":\"SALE\",\"priceDeposit\":300000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private MockHttpSession login(String loginId) throws Exception {
        userService.create(new CreateUserRequest(
                loginId, loginId, null, "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L));
        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"password1!\"}"))
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
        return session;
    }
}
