package banghak.home.halley.application.port.out.cache;

import java.util.Optional;

/**
 * 매물 상세의 곁가지 정보를 담아 두는 캐시 (설계 I158 — TTL 24시간).
 *
 * <p>중개사와 토지이용계획은 <b>거의 바뀌지 않는데</b> 상세 모달을 열 때마다 DB를 왕복합니다.
 * 매물 하나를 여러 번 열어 보는 화면이라 그 왕복이 그대로 체감됩니다.
 *
 * <p><b>JSON 문자열로 담습니다.</b> 담을 것이 응답 형태(DTO)라 포트에 그 타입을 끌어들이면
 * 캐시가 화면 모양에 묶입니다 — 직렬화는 부르는 쪽이 합니다.
 *
 * <p>Redis가 죽어도 <b>조용히 건너뜁니다</b>(2.1.1). 캐시가 없으면 DB에서 읽으면 됩니다 —
 * 캐시 장애가 화면을 막을 이유가 없습니다.
 */
public interface PropertyDetailCache {

    /** 중개사 목록. */
    String AGENTS = "agents";
    /** 토지이용계획. */
    String LAND_USE = "landuse";

    Optional<String> get(String namespace, long propertyId);

    void put(String namespace, long propertyId, String json);

    /** 그 매물만 지운다 — 저장·갱신 직후에 부른다. */
    void evict(String namespace, long propertyId);

    /**
     * 그 갈래를 통째로 지운다.
     *
     * <p>중개사 정보를 고치면 <b>그 중개사가 붙은 매물 전부</b>가 낡습니다. 어느 매물인지
     * 되짚는 것보다 통째로 버리는 편이 단순하고, 중개사 수정은 드뭅니다.
     */
    void evictAll(String namespace);
}
