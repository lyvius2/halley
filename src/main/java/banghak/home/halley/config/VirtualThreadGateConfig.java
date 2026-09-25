package banghak.home.halley.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class VirtualThreadGateConfig {

    /** 등록 후 보정용. 기존 주입 지점이 이것을 받는다.  */
    @Bean
    @Primary
    public VirtualThreadGate enrichmentGate(
            @Value("${enrichment.max-concurrency:400}") int maxConcurrency) {
        return new VirtualThreadGate("enrichment", maxConcurrency);
    }

    /** 가격 전망용 — 한 매물에 60번을 던지므로 훨씬 좁게 잡는다.  */
    @Bean
    public VirtualThreadGate forecastGate(
            @Value("${forecast.max-concurrency:6}") int maxConcurrency) {
        return new VirtualThreadGate("forecast", maxConcurrency);
    }

    @Bean
    public VirtualThreadGate itineraryGate(
            @Value("${itinerary.max-concurrency:8}") int maxConcurrency) {
        return new VirtualThreadGate("itinerary", maxConcurrency);
    }

    @Bean
    public RateGate ministryRateGate(
            @Value("${ministry.permits-per-second:4}") double permitsPerSecond) {
        return new RateGate("ministry", permitsPerSecond);
    }
}
