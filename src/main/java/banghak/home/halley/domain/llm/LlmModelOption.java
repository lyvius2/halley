package banghak.home.halley.domain.llm;

/**
 * 고를 수 있는 모델 하나 (설계 I267).
 *
 * @param id          호출에 실리는 이름 — `claude-opus-5`
 * @param displayName 사람이 읽는 이름 — Anthropic 이 준 그대로
 */
public record LlmModelOption(String id, String displayName) {
}
