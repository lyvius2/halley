package banghak.home.halley.adapter.outbound.external.vworld;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

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
