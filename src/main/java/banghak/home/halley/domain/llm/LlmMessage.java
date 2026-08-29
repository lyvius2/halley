package banghak.home.halley.domain.llm;

/**
 * LLM에 보내는 한 번의 요청 (설계 I58).
 *
 * <p>공급자마다 파라미터 이름이 다르므로(Claude는 `system`이 최상위, Ollama는 메시지 배열의 한 역할)
 * 포트에서는 <b>공통분모만</b> 담고 변환은 어댑터가 맡습니다.
 *
 * @param system    역할·출력 형식 지시
 * @param user      실제 질문
 * @param maxTokens 응답 상한 — 비용을 묶는 유일한 손잡이라 호출자가 정한다
 * @param model     쓸 모델. null이면 공급자 기본값을 쓴다. 작업마다 무게가 달라
 *                  호출자가 고를 수 있어야 한다 (설계 I73)
 */
public record LlmMessage(String system, String user, int maxTokens, String model) {

    /** 공급자 기본 모델을 쓰는 요청. */
    public LlmMessage(String system, String user, int maxTokens) {
        this(system, user, maxTokens, null);
    }
}
