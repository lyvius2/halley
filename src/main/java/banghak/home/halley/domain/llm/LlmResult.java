package banghak.home.halley.domain.llm;

import java.util.Optional;

/**
 * LLM 응답 (설계 I58).
 *
 * <p>실패를 예외로 던지지 않습니다. LLM은 없어도 나머지 채점이 돌아야 하는 <b>보조 입력</b>이라,
 * 호출 측이 `text()`가 비었는지만 보고 넘어갈 수 있어야 합니다 — 다른 외부 연동의 fallback과 같은 태도입니다.
 *
 * @param text         모델이 낸 본문. 실패면 null
 * @param model        실제로 응답한 모델 이름 — 무엇으로 매긴 점수인지 남겨야 한다
 * @param failureCause 실패 사유. 성공이면 null
 */
public record LlmResult(String text, String model, String failureCause) {

    public static LlmResult of(String text, String model) {
        return new LlmResult(text, model, null);
    }

    public static LlmResult failed(String cause) {
        return new LlmResult(null, null, cause);
    }

    public boolean isPresent() {
        return text != null && !text.isBlank();
    }

    public Optional<String> value() {
        return isPresent() ? Optional.of(text) : Optional.empty();
    }
}
