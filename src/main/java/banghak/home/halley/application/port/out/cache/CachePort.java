package banghak.home.halley.application.port.out.cache;

import java.time.Duration;
import java.util.Optional;

public interface CachePort {

    /** 매물에 붙은 중개사 목록. 키는 매물 번호  */
    String AGENTS = "agents";
    /** 토지이용계획. 키는 매물 번호  */
    String LAND_USE = "landuse";
    /** 임장 플래너에서 작업 중인 것. 키는 사용자 번호  */
    String ITINERARY = "itinerary";

    String REFERENCE_MISS = "refmiss";

    String REFERENCE_LOOKING = "reflooking";

    String ENRICHING = "enriching";

    String CRITERION = "criterion";
    /** 항목 가중치. 순위를 바꾸면 모든 매물의 총점이 달라집니다  */
    String CRITERION_WEIGHT = "criterionweight";
    /** 규제 파라미터. 키는 프로파일. 틀리면 대출 한도가 틀립니다  */
    String REGULATION_PARAM = "regparam";
    /** 운영 설정. 관리자 화면에서 자주 만지므로 수명을 짧게 둡니다  */
    String SYSTEM_CONFIG = "sysconfig";
    /** 법정동코드 사전. 사전 재적재 말고는 바뀌지 않습니다  */
    String LEGAL_DONG = "legaldong";

    String NEWS = "news";

    /** 쓸 수 있는 Claude 모델 목록 — 자주 안 바뀌므로 48시간 담아 둔다.  */
    String LLM_MODELS = "llmmodels";

    String DRIVE_ROUTE = "driveroute";

    String LOGIN_FAILURES = "loginfail";

    Optional<String> get(String namespace, String key);

    void put(String namespace, String key, String json, Duration ttl);

    void evict(String namespace, String key);

    void evictAll(String namespace);
}
