package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.llm.LlmFeature;
import banghak.home.halley.domain.llm.LlmModelOption;

import java.util.List;
import java.util.Map;

/** AI 모델 설정 화면이 한 번에 받는 것. */
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
