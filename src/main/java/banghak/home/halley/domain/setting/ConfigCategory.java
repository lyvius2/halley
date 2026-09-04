package banghak.home.halley.domain.setting;

public enum ConfigCategory {
    SLACK,
    BATCH,
    SCORING,
    LOAN,
    /** 어느 자리에 어떤 모델을 쓸 것인가 (설계 I267). */
    LLM
}
