package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.llm.LlmFeature;
import banghak.home.halley.domain.llm.LlmModelOption;

import java.util.List;
import java.util.Map;

/**
 * AI 모델 설정 화면이 한 번에 받는 것 (설계 I267).
 *
 * @param features 자리 넷 — 무엇을 하는 자리인지까지 함께 보낸다
 * @param models   고를 수 있는 모델. 비어 있지 않다 — 못 받으면 지금 쓰는 것들이 온다
 */
public record LlmModelSettingsResponse(
        List<FeatureSetting> features,
        List<LlmModelOption> models) {

    public record FeatureSetting(String key, String label, String description, String model) {
    }

    public static LlmModelSettingsResponse of(Map<LlmFeature, String> chosen,
                                              List<LlmModelOption> models) {
        return new LlmModelSettingsResponse(
                chosen.entrySet().stream()
                        .map(e -> new FeatureSetting(e.getKey().configKey(), e.getKey().label(),
                                e.getKey().description(), e.getValue()))
                        .toList(),
                models);
    }
}
