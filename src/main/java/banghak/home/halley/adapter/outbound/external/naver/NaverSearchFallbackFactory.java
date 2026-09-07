package banghak.home.halley.adapter.outbound.external.naver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class NaverSearchFallbackFactory implements FallbackFactory<NaverSearchFeignClient> {

    @Override
    public NaverSearchFeignClient create(Throwable cause) {
        return (clientId, clientSecret, query, display, sort) -> {
            log.warn("Naver news search failed - returning no articles. query={}, cause={}",
                    query, describe(cause));
            return null;
        };
    }
}
