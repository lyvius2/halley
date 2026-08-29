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
 */
public record LlmMessage(String system, String user, int maxTokens) {
}
