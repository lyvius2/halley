package banghak.home.halley.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class LegalDongCodeServiceTest {

    @Autowired
    private LegalDongCodeService legalDongCodeService;

    @Test
    @DisplayName("지번주소에서 시군구코드를 역매핑한다")
    void deriveFromJibun() {
        // when
        final var code = legalDongCodeService.deriveSigunguCode("서울특별시 마포구 서교동 12-3");

        // then
        assertThat(code).contains("11680");
    }

    @Test
    @DisplayName("매핑되지 않는 주소는 빈 값을 반환한다")
    void deriveUnknownAddress() {
        // when
        final var code = legalDongCodeService.deriveSigunguCode("서울특별시 강남구 논현동 1");

        // then
        assertThat(code).isEmpty();
    }
}
