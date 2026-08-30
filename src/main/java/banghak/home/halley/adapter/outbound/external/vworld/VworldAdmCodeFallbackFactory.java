package banghak.home.halley.adapter.outbound.external.vworld;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

/**
 * 실패하면 시군구 사전이 비고, 그러면 규제지역 적재가 통째로 실패합니다 — 조용히 넘기면
 * 왜 규제지역이 안 들어왔는지 알 수 없습니다 (설계 I78).
 */
@Slf4j
@Component
public class VworldAdmCodeFallbackFactory implements FallbackFactory<VworldAdmCodeFeignClient> {

    @Override
    public VworldAdmCodeFeignClient create(Throwable cause) {
        return new VworldAdmCodeFeignClient() {
            @Override
            public String sido(String key, String format, int numOfRows, int pageNo) {
                log.warn("VWorld sido lookup failed - sigungu dictionary will be empty. cause={}",
                        describe(cause));
                return null;
            }

            @Override
            public String sigungu(String key, String admCode, String format, int numOfRows, int pageNo) {
                log.warn("VWorld sigungu lookup failed. sidoCode={}, cause={}", admCode, describe(cause));
                return null;
            }
        };
    }
}
