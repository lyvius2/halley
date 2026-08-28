package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;

/**
 * LLM 공급자 추상화 (설계 I58).
 *
 * <p>지금 구현체는 Claude(`ClaudeLlmAdapter`) 하나지만, 나중에 Ollama 같은 로컬 모델을 붙일 수 있게
 * <b>공급자에 종속된 개념을 넣지 않습니다</b> — 프롬프트 캐싱·툴 호출·스트리밍은 여기 없습니다.
 * 여러 구현이 공존하면 `halley.llm.provider`로 고릅니다.
 *
 * <p>실패는 예외가 아니라 {@link LlmResult#failed(String)}로 돌려줍니다. LLM은 보조 입력이라
 * 죽어도 나머지 채점은 그대로 나와야 합니다 (설계 12.2 원칙).
 */
public interface LlmPort {

    /** 이 구현체가 어떤 공급자인지 — 로그·설정 매칭용. */
    String provider();

    /** 설정이 갖춰져 실제로 호출할 수 있는 상태인지. 키가 없으면 false. */
    boolean isEnabled();

    LlmResult complete(LlmMessage message);
}
