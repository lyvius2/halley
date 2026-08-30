package banghak.home.halley.adapter.outbound.external.law;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

/**
 * 조회 실패는 <b>반드시 로그를 남깁니다</b>. 규제지역을 못 받으면 비규제(LTV 0.7)로 계산되어
 * 한도가 과대평가되는데, 조용히 넘어가면 그 사실을 알 방법이 없습니다 (설계 I73).
 */
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
