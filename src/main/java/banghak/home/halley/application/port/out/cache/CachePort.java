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
     * "지금 누가 받아 오고 있다" (설계 I262).
     *
     * <p>[I259]에서 화면이 <b>3초마다 다시 묻게</b> 했는데, 물을 때마다 배경 조회를
     * 새로 띄웠습니다 — 1분이면 스무 벌입니다. 표시가 있으면 <b>기다리기만</b> 합니다.
     *
     * <p>TTL은 <b>끝났는데 표시가 남는 것</b>을 막는 안전장치입니다. 정상 종료 때는
     * 지우고, 프로세스가 죽어 못 지웠으면 이 시간이 지나 저절로 풀립니다.
     */
    String REFERENCE_LOOKING = "reflooking";

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

    /**
     * 사람이 손대야만 바뀌는 기준 정보 (설계 I239 · `docs/ADJUST_CACHE.md` §2.1).
     *
     * <p>읽기는 매우 잦고 쓰기는 거의 없습니다 — 캐시가 가장 잘 듣는 모양입니다.
     * <b>바뀌는 지점이 명확</b>해서 무효화가 쉽다는 것이 더 중요합니다.
     */
    String CRITERION = "criterion";
    /** 항목 가중치. 순위를 바꾸면 <b>모든 매물의 총점</b>이 달라집니다 */
    String CRITERION_WEIGHT = "criterionweight";
    /** 규제 파라미터. 키는 프로파일. <b>틀리면 대출 한도가 틀립니다</b> */
    String REGULATION_PARAM = "regparam";
    /** 운영 설정. 관리자 화면에서 자주 만지므로 수명을 짧게 둡니다 */
    String SYSTEM_CONFIG = "sysconfig";
    /** 법정동코드 사전. 사전 재적재 말고는 바뀌지 않습니다 */
    String LEGAL_DONG = "legaldong";

    /**
     * 관련 기사 검색 결과 (설계 I246). 키는 <b>검색어</b>다.
     *
     * <p>매물 번호가 아닙니다 — 같은 단지의 매물 둘은 검색어가 같고, 그러면
     * <b>한 번만 물으면 됩니다.</b>
     */
    String NEWS = "news";

    /**
     * 자가용 길 하나 (설계 I272).
     *
     * <p>열쇠는 <b>좌표 넷과 출발 시각</b>입니다. 시각을 빼면 화요일 14시와 일요일
     * 14시가 같은 길이 되어 [I196]이 무의미해집니다.
     *
     * <p>매물 일곱이면 한 번 계산에 <b>49쌍</b>입니다. 담아 두지 않으면 「경로 계산」을
     * 누를 때마다 49건이 나가고, 한도가 있는 API는 그것으로 하루가 끝납니다 —
     * 실제로 그렇게 끝났습니다([I270]).
     */
    String DRIVE_ROUTE = "driveroute";

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
