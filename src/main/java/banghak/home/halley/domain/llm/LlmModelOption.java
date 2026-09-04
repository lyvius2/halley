package banghak.home.halley.domain.llm;

/**
 * 고를 수 있는 모델 하나 (설계 I267).
 *
 * @param id          호출에 실리는 이름 — `claude-opus-5`
 * @param displayName 사람이 읽는 이름 — Anthropic 이 준 그대로
 * @param special     Fable 계열처럼 성격이 다른 모델인가 (설계 I278).
 *                     Anthropic 목록 API에 이를 가리키는 필드가 없어 <b>id 이름 규칙</b>으로 가린다.
 */
public record LlmModelOption(String id, String displayName, boolean special) {

    /** Anthropic 이 아직 "특수 모델"이라는 필드를 안 준다 — id 접두어로 가린다 (설계 I278). */
    private static final String SPECIAL_ID_PREFIX = "claude-fable";

    public static LlmModelOption of(String id, String displayName) {
        return new LlmModelOption(id, displayName, id != null && id.startsWith(SPECIAL_ID_PREFIX));
    }
}
