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
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PropertyApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("비인증 상태로 매물 목록을 조회하면 401을 반환한다")
    void unauthenticatedListIsUnauthorized() throws Exception {
        // when
        mockMvc.perform(get("/api/properties"))

                // then
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증 세션으로 매물 등록·조회·수정·삭제가 정상 동작한다")
    void crudFlowWithAuthenticatedSession() throws Exception {
        // given
        userService.create(new CreateUserRequest(
                "prop-user", "prop@example.com", "password1!", UserRole.MEMBER, null, null, null, 0L));

        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prop@example.com\",\"password\":\"password1!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1!\",\"newPassword\":\"newpassword2!\"}"))
                .andExpect(status().isNoContent());

        final String createBody = """
                {"name":"한빛아파트","dealType":"SALE","priceDeposit":550000000,"addressRoad":"서울시 도로명주소"}
                """;

        // when
        final String createdBody = mockMvc.perform(post("/api/properties").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("한빛아파트"))
                .andExpect(jsonPath("$.sourceType").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
        final String id = objectMapper.readTree(createdBody).get("id").asString();

        // then
        mockMvc.perform(get("/api/properties").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("한빛아파트"));

        mockMvc.perform(get("/api/properties/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        final String updateBody = """
                {"name":"한빛아파트2","dealType":"JEONSE","priceDeposit":350000000}
                """;
        mockMvc.perform(put("/api/properties/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("한빛아파트2"))
                .andExpect(jsonPath("$.dealType").value("JEONSE"));

        mockMvc.perform(delete("/api/properties/" + id).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/properties/" + id).session(session))
                .andExpect(status().isNotFound());
    }
}
