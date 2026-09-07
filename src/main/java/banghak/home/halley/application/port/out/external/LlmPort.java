package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;

/** LLM 공급자 추상화. */
public interface LlmPort {

 /** 이 구현체가 어떤 공급자인지. 로그·설정 매칭용. */
    String provider();

 /** 설정이 갖춰져 실제로 호출할 수 있는 상태인지. 키가 없으면 false. */
    boolean isEnabled();

    LlmResult complete(LlmMessage message);
}
