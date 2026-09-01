package banghak.home.halley.adapter.outbound.external.naver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 네이버 검색을 <b>어디로, 무슨 헤더로</b> 부르는가 (설계 I235).
 *
 * <p>2026년에 `openapi.naver.com` 에서 네이버 클라우드 <b>API Hub</b> 로 옮겨졌습니다.
 * 같은 키로 옛 주소는 <b>401</b>, 새 주소는 <b>200</b> 입니다(실측).
 *
 * <p>응답 파싱은 `NaverNewsAdapterTest` 가 봅니다. <b>여기서 보는 것은 주소와
 * 헤더뿐</b>입니다 — 이번에 깨진 곳이 정확히 거기였고, 파서 테스트는
 * 멀쩡한 채로 통과하고 있었습니다.
 */
@DisplayName("네이버 API Hub 계약 (설계 I235)")
class NaverApiHubContractTest {

    @Test
    @DisplayName("API Hub 주소를 쓴다 — openapi.naver.com 은 401 이다")
    void usesTheApiHubHost() {
        final FeignClient annotation = NaverSearchFeignClient.class.getAnnotation(FeignClient.class);

        assertThat(annotation.url())
                .contains("naverapihub.apigw.ntruss.com")
                .doesNotContain("openapi.naver.com");
    }

    /**
     * 헤더 이름이 바뀐 것이 <b>이번 장애의 원인</b>이었습니다.
     * 주소만 옮기고 헤더를 그대로 두면 다시 401 입니다.
     */
    @Test
    @DisplayName("NCP 게이트웨이 헤더로 인증한다 — X-Naver-Client-* 는 더 이상 안 통한다")
    void authenticatesWithNcpHeaders() {
        final Method search = searchNews();
        final Parameter[] params = search.getParameters();

        assertThat(headerName(params[0])).isEqualTo("X-NCP-APIGW-API-KEY-ID");
        assertThat(headerName(params[1])).isEqualTo("X-NCP-APIGW-API-KEY");
    }

    /**
     * 새 주소는 `.json` 으로 끝나지 않습니다. 형식은 `format` 파라미터로 정하는데
     * 기본이 `json` 이라 안 보냅니다.
     */
    @Test
    @DisplayName("경로는 /news — 옛 /news.json 이 아니다")
    void pathHasNoJsonSuffix() {
        final String[] path = searchNews().getAnnotation(GetMapping.class).value();

        assertThat(path).containsExactly("/news");
    }

    private Method searchNews() {
        return java.util.Arrays.stream(NaverSearchFeignClient.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("searchNews"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("searchNews 가 없다"));
    }

    private String headerName(Parameter parameter) {
        final RequestHeader header = parameter.getAnnotation(RequestHeader.class);
        assertThat(header).as("%s 에 @RequestHeader 가 없다", parameter.getName()).isNotNull();
        return header.value();
    }
}
