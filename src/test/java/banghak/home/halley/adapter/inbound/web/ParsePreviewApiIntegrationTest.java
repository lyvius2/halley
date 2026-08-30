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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ParsePreviewApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("붙여넣기 텍스트를 파싱해 프리뷰 필드를 반환한다")
    void parsePreview() throws Exception {
        // given
        userService.create(new CreateUserRequest(
                "parse", "parse-user", null, "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L));
        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"parse\",\"password\":\"password1!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1!\",\"newPassword\":\"newpassword2!\"}"))
                .andExpect(status().isNoContent());

        final String body = """
                {"text":"매매\\n단지명\\n독립문삼호\\n매매가\\n15억\\n전용면적\\n84.98㎡\\n매물번호\\nA1"}
                """;

        // when
        final String response = mockMvc.perform(post("/api/properties/parse-preview").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        final JsonNode fields = objectMapper.readTree(response).path("fields");
        assertThat(fieldValue(fields, "dealType")).isEqualTo("매매");
        assertThat(fieldValue(fields, "priceDeposit")).isEqualTo("1500000000");
        assertThat(fieldValue(fields, "name")).isEqualTo("독립문삼호");
    }

    private String fieldValue(JsonNode fields, String key) {
        for (final JsonNode field : fields) {
            if (key.equals(field.path("key").asString())) {
                return field.path("value").asString(null);
            }
        }
        return null;
    }
}
