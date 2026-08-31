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
        // 검색어는 단지명이라 로그에 남겨도 된다. 키는 남기지 않는다
        return (clientId, clientSecret, query, display, sort) -> {
            log.warn("Naver news search failed - returning no articles. query={}, cause={}",
                    query, describe(cause));
            return null;
        };
    }
}
