package banghak.home.halley.adapter.outbound.external.ecos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class EcosFallbackFactory implements FallbackFactory<EcosFeignClient> {

    @Override
    public EcosFeignClient create(Throwable cause) {
        // 인증키는 경로에 들어간다. 로그에 남기지 않는다
        return (key, start, end, statCode, cycle, from, to) -> {
            log.warn("ECOS call failed - returning no series. statCode={}, cycle={}, period={}~{}, cause={}",
                    statCode, cycle, from, to, describe(cause));
            return null;
        };
    }
}
