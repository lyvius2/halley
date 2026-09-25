package banghak.home.halley.support;

import jakarta.servlet.http.Cookie;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 통합 시험의 모든 요청에 유효한 CSRF 토큰을 싣는다 (설계 I295).
 *
 * <p>CSRF 를 켜면 토큰 없는 POST 는 403 이다. 시험이 그걸 하나하나 붙이면 수십 곳이 바뀌고,
 * 하나 빠지면 그 시험은 <b>보안이 아니라 토큰 누락</b>을 재는 시험이 된다.
 *
 * <p><b>실제 저장소를 그대로 지나갑니다.</b> 쿠키의 값과 헤더의 값이 같으면 통과하는 것이
 * 운영과 같은 규칙이다. spring-security-test 의 {@code csrf()} 는 필터의 저장소를
 * 시험용으로 <b>바꿔치기</b>해서, 그 뒤로는 실제 쿠키가 내려오는지 볼 수 없게 만든다.
 */
@Component
public class CsrfMockMvcCustomizer implements MockMvcBuilderCustomizer {

    public static final String TOKEN = "test-csrf-token";

    @Override
    public void customize(ConfigurableMockMvcBuilder<?> builder) {
        builder.defaultRequest(get("/")
                .cookie(new Cookie("XSRF-TOKEN", TOKEN))
                .header("X-XSRF-TOKEN", TOKEN));
    }
}
