package banghak.home.halley.domain.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fable 계열을 <b>id 이름 규칙</b>으로 가린다 (설계 I278).
 *
 * <p>Anthropic {@code /v1/models} 응답을 실제로 받아 봤습니다 — {@code type}·{@code id}·
 * {@code display_name}·{@code created_at}·{@code max_input_tokens}·{@code max_tokens}·
 * {@code capabilities} 뿐이고, "이건 실험적/특수 모델"이라고 가리키는 필드는 없습니다.
 * 유일한 신호는 {@code id} 가 {@code claude-fable-} 로 시작한다는 것뿐입니다.
 */
@DisplayName("Fable 계열 판정 (설계 I278)")
class LlmModelOptionTest {

    @Test
    @DisplayName("id가 claude-fable 로 시작하면 특수 모델이다")
    void detectsFableModels() {
        assertThat(LlmModelOption.of("claude-fable-5-1", "Claude Fable 5.1").special()).isTrue();
        assertThat(LlmModelOption.of("claude-fable-5", "Claude Fable 5").special()).isTrue();
    }

    @Test
    @DisplayName("보통 모델은 특수하지 않다")
    void ordinaryModelsAreNotSpecial() {
        assertThat(LlmModelOption.of("claude-opus-5", "Claude Opus 5").special()).isFalse();
        assertThat(LlmModelOption.of("claude-sonnet-4-5-20250929", "Claude Sonnet 4.5").special()).isFalse();
    }

    @Test
    @DisplayName("이름 중간에 fable 이 있어도 접두어가 아니면 특수하지 않다")
    void doesNotMatchFableInTheMiddle() {
        // 실제로 있는 이름은 아니지만, "접두어" 규칙임을 분명히 해 둔다
        assertThat(LlmModelOption.of("claude-opus-fable-5", "Claude Opus Fable 5").special()).isFalse();
    }
}
