package banghak.home.halley.domain.llm;

import java.util.Arrays;
import java.util.Optional;

/**
 * AI를 쓰는 자리 — <b>자리마다 모델을 따로 고른다</b> (설계 I267).
 *
 * <p>지금까지는 환경변수 하나(`LLM_CLAUDE_MODEL`)가 전부였습니다. 그런데 네 자리는
 * 성격이 다릅니다 — 판단이 흔들리면 안 되는 것도 있고, 값이 싸고 빨라야 하는 것도
 * 있습니다. 하나로 묶어 두면 <b>가장 비싼 자리에 맞춰</b>야 합니다.
 *
 * <p>고른 값은 {@code system_config} 에 둡니다. 이미 관리자 설정이 쓰는 표이고,
 * 여기에 새 표를 만들면 <b>설정이 두 군데</b>가 됩니다.
 */
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
