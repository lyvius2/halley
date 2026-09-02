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
 *
 * <p><b>동시 실행 상한만으로는 모자랐습니다.</b> 국토부는 초당 요청 수를 세므로
 * {@link RateGate}를 따로 둡니다 (설계 I140).
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

    /**
     * 임장 경로 계산용 (설계 I263).
     *
     * <p>매물 7개면 구간이 <b>49쌍</b>입니다. 예전에는 이것을 <b>한 줄로</b> 돌았습니다 —
     * 카카오 읽기 제한이 6초이니 최악이면 5분입니다. 프록시가 60초에 끊어
     * <b>504</b>가 났습니다.
     *
     * <p>상한이 작습니다. 길찾기 API는 한꺼번에 몰면 429를 돌려줍니다 —
     * 빨리 하자고 넓히면 다 같이 실패합니다.
     */
    @Bean
    public VirtualThreadGate itineraryGate(
            @Value("${itinerary.max-concurrency:8}") int maxConcurrency) {
        return new VirtualThreadGate("itinerary", maxConcurrency);
    }

    /**
     * 국토부는 <b>초당</b> 요청 수를 셉니다 (설계 I140).
     *
     * <p>동시 실행 상한만으로는 부족했습니다. 6으로 묶어도 각 호출이 40ms에 끝나면
     * 초당 150건이 나가고, 실제로 그렇게 나가서 전부 429를 받았습니다.
     *
     * <p>전망(60개월)과 실거래 카드(12개월)가 <b>같은 키를 씁니다.</b> 그래서 게이트도
     * 하나를 나눠 씁니다 — 따로 두면 둘이 동시에 돌 때 합쳐서 제한을 넘습니다.
     */
    @Bean
    public RateGate ministryRateGate(
            @Value("${ministry.permits-per-second:4}") double permitsPerSecond) {
        return new RateGate("ministry", permitsPerSecond);
    }
}
