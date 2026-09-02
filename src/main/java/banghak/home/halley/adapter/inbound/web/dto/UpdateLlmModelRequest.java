package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 자리 하나의 모델을 고친다 (설계 I267).
 *
 * @param key   {@code LlmFeature.configKey()} — 아는 것만 받는다
 * @param model 비우면 기본값({@code llm.claude.model})으로 되돌아간다
 */
public record UpdateLlmModelRequest(String key, String model) {
}
