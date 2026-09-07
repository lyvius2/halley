package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.llm.LlmJobState;

import java.util.Optional;

/** LLM 작업 상태 캐시. */
public interface LlmJobCache {

 /** LLM 호출 직전에 표시한다. TTL이 걸려 앱이 도중에 죽어도 마커가 영영 남지 않는다. */
    void markRunning(String jobKey);

 /** 응답을 DB에 저장한 뒤 캐시에 넣는다. 순서가 뒤집히면 DB보다 앞선 값이 보인다. */
    void markDone(String jobKey, String payload);

    Optional<LlmJobState> get(String jobKey);

 /** 실패했거나 다시 물어볼 때. 지우지 않으면 옛 답이 계속 나온다. */
    void clear(String jobKey);
}
