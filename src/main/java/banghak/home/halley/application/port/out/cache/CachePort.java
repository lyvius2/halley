package banghak.home.halley.application.port.out.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * 범용 캐시 (설계 I164 · I179).
 *
 * <p>설계 문서(2026-08-24)에 <b>하나의 `CachePort`</b> 로 적혀 있던 것입니다. 구현할 때
 * 타입마다 쪼개져 여덟 개가 됐고, 그 어긋남을 나중 문서가 "계열"이라 부르며 정당화했습니다(I164).
 * 이제 설계대로 되돌립니다.
 *
 * <p><b>키와 TTL 을 부르는 쪽이 정합니다.</b> `PropertyDetailCache`(I158)는 키를 매물 번호로,
 * TTL 을 24시간으로 못 박아 두어 <b>매물에 딸리지 않은 것</b>이나 <b>수명이 다른 것</b>을
 * 담을 수 없었습니다.
 *
 * <p><b>JSON 문자열로 담습니다.</b> 담을 것이 응답 형태(DTO)라 포트에 그 타입을 끌어들이면
 * 캐시가 화면 모양에 묶입니다 — 직렬화는 부르는 쪽이 합니다.
 *
 * <p>Redis 가 죽어도 <b>조용히 건너뜁니다</b>(2.1.1). 캐시가 없으면 원본에서 읽으면 됩니다.
 */
public interface CachePort {

    /** 매물에 붙은 중개사 목록. 키는 매물 번호 */
    String AGENTS = "agents";
    /** 토지이용계획. 키는 매물 번호 */
    String LAND_USE = "landuse";
    /** 임장 플래너에서 작업 중인 것. <b>키는 사용자 번호</b> (설계 I179) */
    String ITINERARY = "itinerary";

    /**
     * "찾아봤지만 없었다" (설계 I219).
     *
     * <p>결과가 아니라 <b>이미 찾아봤다는 사실</b>을 담습니다. 실거래는 못 찾으면
     * 저장할 것이 없어, 저장된 게 없으면 <b>상세를 열 때마다 12개월치를 다시</b>
     * 받아 왔습니다.
     */
    String REFERENCE_MISS = "refmiss";

    /**
     * "이 매물은 지금 보정 중이다" (설계 I220).
     *
     * <p>등록 응답을 <b>기다리지 않고</b> 돌려주므로, 목록·상세가 <b>아직 점수가 없는
     * 매물</b>을 만납니다. 그때 그 자리에서 채점해 버리면 <b>기다림이 옮겨 갔을 뿐</b>
     * 입니다 — 표시가 있으면 "곧 채워진다"고 답하고 넘어갑니다.
     *
     * <p>TTL 을 둡니다. 보정 중에 서버가 죽으면 표시만 남는데, 그러면 그 매물은
     * <b>영영 채점되지 않습니다.</b>
     */
    String ENRICHING = "enriching";

    Optional<String> get(String namespace, String key);

    void put(String namespace, String key, String json, Duration ttl);

    void evict(String namespace, String key);

    /**
     * 그 갈래를 통째로 지운다.
     *
     * <p>중개사 정보를 고치면 <b>그 중개사가 붙은 매물 전부</b>가 낡습니다. 어느 매물인지
     * 되짚는 것보다 통째로 버리는 편이 단순하고, 중개사 수정은 드뭅니다.
     */
    void evictAll(String namespace);
}
