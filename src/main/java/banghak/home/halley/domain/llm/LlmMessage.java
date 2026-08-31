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
 * @param temperature 답의 흔들림. <b>null이면 보내지 않고 공급자 기본값에 맡깁니다</b> —
 *                  기존 호출(AI 추천도·비교 우위)의 동작을 바꾸지 않으려는 것입니다.
 *                  <b>판단하는 작업은 0을 씁니다</b> (설계 I127).
 *                  <p><b>다만 요즘 Claude 모델은 이 값을 받지 않습니다</b>(설계 I144) —
 *                  어댑터가 기본으로 빼고 보냅니다. 뜻은 남겨 둡니다: 받는 모델이나
 *                  다른 공급자로 내려갈 때 켜면 됩니다. <b>같은 지표에 같은 답</b>은
 *                  이제 프롬프트 해시(I59)가 지킵니다 — 다시 묻지 않고 저장된 답을 씁니다.
 */
public record LlmMessage(String system, String user, int maxTokens, String model, Double temperature) {

    /** 공급자 기본 모델을 쓰는 요청. */
    public LlmMessage(String system, String user, int maxTokens) {
        this(system, user, maxTokens, null, null);
    }

    /** 모델만 고르는 요청. */
    public LlmMessage(String system, String user, int maxTokens, String model) {
        this(system, user, maxTokens, model, null);
    }

    /** 흔들리면 안 되는 판단 작업 — {@code temperature = 0}. */
    public static LlmMessage deterministic(String system, String user, int maxTokens, String model) {
        return new LlmMessage(system, user, maxTokens, model, 0.0);
    }
}
