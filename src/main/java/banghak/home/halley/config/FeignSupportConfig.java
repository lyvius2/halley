package banghak.home.halley.config;

import feign.Target;
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

@Configuration
@EnableFeignClients(basePackages = "banghak.home.halley.adapter.outbound.external")
public class FeignSupportConfig {

    /**
     * 서킷브레이커·TimeLimiter의 인스턴스 이름을 <b>`@FeignClient`의 name으로</b> 맞춘다 (설계 I70).
     *
     * <p>기본 규칙은 `ClaudeFeignClientmessagesStringStringString`처럼 <b>클래스명 + 메서드 시그니처</b>로
     * ID를 만듭니다. 그래서 `resilience4j.*.instances.claude-llm` 설정이 <b>어느 것에도 붙지 않고</b>
     * 전부 기본값으로 돌고 있었습니다 — 클라이언트별로 다르게 잡아 둔 임계값이 통째로 무시된 것입니다.
     *
     * <p>메서드 시그니처가 ID에 들어가면 파라미터를 하나 추가하는 것만으로도 설정이 조용히 떨어져
     * 나갑니다. 이름으로 고정해 그 취약함을 없앱니다.
     */
    @Bean
    public CircuitBreakerNameResolver feignClientNameResolver() {
        return (String feignClientName, Target<?> target, Method method) -> feignClientName;
    }
}
