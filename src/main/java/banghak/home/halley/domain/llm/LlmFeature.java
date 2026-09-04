package banghak.home.halley.domain.llm;

import java.util.Arrays;
import java.util.Optional;

/** AI를 쓰는 자리 — 자리마다 모델을 따로 고른다 (설계 I267). */
public enum LlmFeature {

    RECOMMENDATION("llm.model.recommendation", "AI 추천도",
            "매물 하나를 읽고 추천 점수와 이유를 낸다"),
    COMPARATIVE("llm.model.comparative", "비교 우위 분석",
            "매물 전체를 한 번에 견주어 순위를 매긴다"),
    PRICE_FORECAST("llm.model.forecast", "가격 전망",
            "실거래 추세를 읽고 오를지 내릴지 말한다"),
    COMMUTE_ESTIMATE("llm.model.commute", "직주근접 추정",
            "ODsay 가 막혔을 때 대중교통 시간을 추정한다 (설계 I210)");

    private final String configKey;
    private final String label;
    private final String description;

    LlmFeature(String configKey, String label, String description) {
        this.configKey = configKey;
        this.label = label;
        this.description = description;
    }

    public String configKey() {
        return configKey;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public static Optional<LlmFeature> ofConfigKey(String key) {
        return Arrays.stream(values()).filter(f -> f.configKey.equals(key)).findFirst();
    }
}
