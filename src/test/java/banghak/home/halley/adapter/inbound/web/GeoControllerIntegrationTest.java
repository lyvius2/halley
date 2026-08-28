package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.application.service.UserService;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class GeoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        KakaoLocalPort kakaoLocalPort() {
            return new KakaoLocalPort() {
                @Override
                public List<GeoSearchResult> searchAddress(String query) {
                    return List.of(new GeoSearchResult(
                            "서울 마포구 서교동", "서울 마포구 양화로",
                            new BigDecimal("37.55"), new BigDecimal("126.91"), "1144012000"));
                }

                @Override
                public List<PoiResult> searchCategory(String categoryGroupCode, double x, double y, int radius) {
                    return List.of();
                }

                @Override
                public List<PoiResult> searchKeyword(String query, String categoryGroupCode, double x, double y, int radius) {
                    return List.of();
                }
            };
        }
    }

    @Test
    @DisplayName("비인증 상태로 주소 검색을 호출하면 401을 반환한다")
    void unauthenticatedSearchIsUnauthorized() throws Exception {
        // when
        mockMvc.perform(get("/api/geo/search").param("query", "서울시"))

                // then
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증된 사용자가 주소를 검색하면 좌표 결과를 받는다")
    void authenticatedSearchReturnsCoordinates() throws Exception {
        // given
        userService.create(new CreateUserRequest(
                "geo", "geo-user", "geo@example.com", "password1!", UserRole.MEMBER, null, null, null, 0L));
        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"geo\",\"password\":\"password1!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1!\",\"newPassword\":\"newpassword2!\"}"))
                .andExpect(status().isNoContent());

        // when
        mockMvc.perform(get("/api/geo/search").param("query", "서울시").session(session))

                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].addressName").value("서울 마포구 서교동"))
                .andExpect(jsonPath("$[0].lat").value(37.55))
                .andExpect(jsonPath("$[0].lng").value(126.91));
    }
}
