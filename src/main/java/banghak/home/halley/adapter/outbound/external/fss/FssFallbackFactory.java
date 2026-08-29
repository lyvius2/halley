package banghak.home.halley.adapter.outbound.external.fss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class FssFallbackFactory implements FallbackFactory<FssFeignClient> {

    @Override
    public FssFeignClient create(Throwable cause) {
        return (service, auth, topFinGrpNo, pageNo) -> {
            log.warn("FSS call failed - returning no products. service={}, group={}, page={}, cause={}",
                    service, topFinGrpNo, pageNo, describe(cause));
            return null;
        };
    }
}
