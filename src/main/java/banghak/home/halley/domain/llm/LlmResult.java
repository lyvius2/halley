package banghak.home.halley.domain.llm;

import java.util.Optional;

/** LLM 응답. */
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
