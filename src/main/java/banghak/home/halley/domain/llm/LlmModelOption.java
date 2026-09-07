package banghak.home.halley.domain.llm;

/** 고를 수 있는 모델 하나. */
public record LlmModelOption(String id, String displayName, boolean special) {

 /** Anthropic 이 아직 "특수 모델"이라는 필드를 안 준다. id 접두어로 가린다. */
    private static final String SPECIAL_ID_PREFIX = "claude-fable";

    public static LlmModelOption of(String id, String displayName) {
        return new LlmModelOption(id, displayName, id != null && id.startsWith(SPECIAL_ID_PREFIX));
    }
}
