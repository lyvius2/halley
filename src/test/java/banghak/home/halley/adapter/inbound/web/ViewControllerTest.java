package banghak.home.halley.adapter.inbound.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("루트 경로는 index 셸을 반환하고 카카오 JS 키를 모델에 담는다")
    void shellReturnsIndexWithKakaoJsKey() throws Exception {
        // when
        mockMvc.perform(get("/"))

                // then
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("kakaoJsKey"));
    }
}
