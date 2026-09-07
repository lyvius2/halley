package banghak.home.halley.domain.llm;

/** LLM에 보내는 한 번의 요청. */
public record LlmMessage(String system, String user, int maxTokens, String model, Double temperature) {

 /** 공급자 기본 모델을 쓰는 요청. */
    public LlmMessage(String system, String user, int maxTokens) {
        this(system, user, maxTokens, null, null);
    }

 /** 모델만 고르는 요청. */
    public LlmMessage(String system, String user, int maxTokens, String model) {
        this(system, user, maxTokens, model, null);
    }

 /** 흔들리면 안 되는 판단 작업. temperature = 0. */
    public static LlmMessage deterministic(String system, String user, int maxTokens, String model) {
        return new LlmMessage(system, user, maxTokens, model, 0.0);
    }
}
