package banghak.home.halley.adapter.outbound.external.law;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class LawNoticeFallbackFactory implements FallbackFactory<LawNoticeFeignClient> {

    @Override
    public LawNoticeFeignClient create(Throwable cause) {
        return new LawNoticeFeignClient() {
            @Override
            public String search(String oc, String target, String type, String query) {
                log.warn("Law notice search failed - regulated areas may be stale. query={}, cause={}",
                        query, describe(cause));
                return null;
            }

            @Override
            public String detail(String oc, String target, String type, String id) {
                log.warn("Law notice detail failed - regulated areas may be stale. id={}, cause={}",
                        id, describe(cause));
                return null;
            }

            @Override
            public byte[] download(String flSeq) {
                log.warn("Law notice attachment download failed - cannot read the status table. "
                        + "flSeq={}, cause={}", flSeq, describe(cause));
                return null;
            }
        };
    }
}
