package banghak.home.halley.application.port.out.cache;

/**
 * 매물별 채점 잠금 (설계 I84).
 *
 * <p>한 매물의 채점이 <b>여러 경로에서 겹칩니다</b> — 보정이 끝났을 때, AI 응답이 왔을 때,
 * 사용자가 점수를 저장했을 때. 앞의 둘은 비동기라 시간이 겹칠 수 있고, 그러면 같은 매물의
 * `property_score`를 동시에 쓰게 됩니다.
 *
 * <p><b>TTL이 반드시 필요합니다.</b> 잠근 채 앱이 죽으면 그 매물은 영영 다시 채점되지
 * 않습니다 — 자물쇠를 푸는 코드가 도달하지 못하기 때문입니다.
 *
 * <p>캐시 장애 시에는 <b>잠근 것으로 치지 않고 통과시킵니다</b>(설계 2.1.1). 채점은 멱등하게
 * upsert 하므로 겹쳐도 마지막 값이 남을 뿐이고, 잠금 실패로 채점 자체가 멈추는 편이 더 나쁩니다.
 */
public interface ScoringLock {

    /** @return 이 호출이 잠갔으면 true. 이미 누가 채점 중이면 false */
    boolean tryLock(Long propertyId);

    void unlock(Long propertyId);
}
