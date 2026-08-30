package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.llm.LlmJobState;

import java.util.Optional;

/**
 * LLM 작업 상태 캐시 (설계 I72).
 *
 * <p>화면이 "지금 분석 중인가"를 물어볼 곳입니다. DB에는 그 정보가 없습니다 — 결과가 저장되기
 * <b>전</b>의 상태이기 때문입니다.
 *
 * <p><b>캐시는 가속기지 진실이 아닙니다.</b> 미스가 나면 호출 측이 DB로 내려가야 합니다.
 * TTL이 만료되거나 Redis가 재시작했다고 해서 "결과 없음"으로 답하면, DB에 멀쩡히 있는 값을
 * 못 산출로 보여주게 됩니다.
 *
 * <p>Redis 장애 시에는 조용히 건너뜁니다(설계 2.1.1) — 캐시가 죽어도 DB 조회로 흡수됩니다.
 */
public interface LlmJobCache {

    /** LLM 호출 직전에 표시한다. TTL이 걸려 앱이 도중에 죽어도 마커가 영영 남지 않는다. */
    void markRunning(String jobKey);

    /** 응답을 DB에 저장한 <b>뒤</b> 캐시에 넣는다. 순서가 뒤집히면 DB보다 앞선 값이 보인다. */
    void markDone(String jobKey, String payload);

    Optional<LlmJobState> get(String jobKey);

    /** 실패했거나 다시 물어볼 때. 지우지 않으면 옛 답이 계속 나온다. */
    void clear(String jobKey);
}
