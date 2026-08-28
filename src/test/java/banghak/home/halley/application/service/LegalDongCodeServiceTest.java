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
    @DisplayName("테이블에 없고 카카오 키도 없으면 예외 없이 빈 값을 반환한다")
    void deriveUnknownAddressWithoutKey() {
        // when — 테스트 환경에는 kakao.rest-key가 없어 어댑터가 예외를 던진다
        final var code = legalDongCodeService.deriveSigunguCode("서울특별시 강남구 논현동 1");

        // then
        assertThat(code).isEmpty();
    }

    @Test
    @DisplayName("주소가 비어 있으면 조회하지 않는다")
    void blankAddress() {
        // then
        assertThat(legalDongCodeService.deriveSigunguCode(null)).isEmpty();
        assertThat(legalDongCodeService.deriveSigunguCode("  ")).isEmpty();
    }
}
