package banghak.home.halley.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 가상 스레드 게이트를 <b>둘</b> 둔다 (설계 I129).
 *
 * <p>상한을 하나로 나눠 쓰면 <b>60개월을 훑는 전망 하나가 다른 매물의 보정을 전부
 * 밀어냅니다.</b> 가격 전망은 한 매물에 60번을 던지는데, 보정은 매물당 몇 번이라
 * 같은 줄에 세우면 뒤엣것이 하염없이 기다립니다.
 *
 * <p>전망 쪽 상한이 <b>훨씬 작습니다</b>(6 대 400). 공공 API에 60건을 한꺼번에 던지면
 * 429가 돌아옵니다 — 값이 싼 가상 스레드와 달리 <b>그 끝에 붙은 API는 값이 비쌉니다.</b>
 */
@Configuration
public class VirtualThreadGateConfig {

    /** 등록 후 보정용 (설계 I108). 기존 주입 지점이 이것을 받는다. */
    @Bean
    @Primary
    public VirtualThreadGate enrichmentGate(
            @Value("${enrichment.max-concurrency:400}") int maxConcurrency) {
        return new VirtualThreadGate("enrichment", maxConcurrency);
    }

    /** 가격 전망용 — 한 매물에 60번을 던지므로 훨씬 좁게 잡는다. */
    @Bean
    public VirtualThreadGate forecastGate(
            @Value("${forecast.max-concurrency:6}") int maxConcurrency) {
        return new VirtualThreadGate("forecast", maxConcurrency);
    }
}
