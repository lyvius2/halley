package banghak.home.halley.adapter.outbound.external.ministry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class MinistryReferenceFallbackFactory implements FallbackFactory<MinistryReferenceFeignClient> {

    /**
     * 오퍼레이션이 둘이 되어 람다로는 안 됩니다 — 익명 클래스로 각각 로그를 남깁니다
     * (설계 I131). 어느 쪽이 실패했는지 구분되지 않으면 원인을 못 찾습니다.
     */
    @Override
    public MinistryReferenceFeignClient create(Throwable cause) {
        return new MinistryReferenceFeignClient() {

            @Override
            public String fetchTrade(String serviceKey, String lawdCd, String dealYmd) {
                log.warn("Ministry trade lookup failed - returning empty. lawdCd={}, dealYmd={}, cause={}",
                        lawdCd, dealYmd, describe(cause));
                return null;
            }

            @Override
            public String fetchRent(String serviceKey, String lawdCd, String dealYmd) {
                log.warn("Ministry rent lookup failed - returning empty. lawdCd={}, dealYmd={}, cause={}",
                        lawdCd, dealYmd, describe(cause));
                return null;
            }
        };
    }
}
