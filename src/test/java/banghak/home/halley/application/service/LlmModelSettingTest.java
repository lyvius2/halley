package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.UpdateLlmModelRequest;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.application.port.out.external.ClaudeModelsPort;
import banghak.home.halley.domain.llm.LlmFeature;
import banghak.home.halley.domain.llm.LlmModelOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 자리마다 모델을 따로 고른다 (설계 I267).
 *
 * <p>지금까지는 환경변수 하나가 전부라 <b>바꾸려면 배포</b>해야 했습니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("AI 모델 설정 (설계 I267)")
class LlmModelSettingTest {

    static final AtomicInteger LIST_CALLS = new AtomicInteger();

    @TestConfiguration
    static class Models {

        @Bean
        @Primary
        ClaudeModelsPort claudeModelsPort() {
            return () -> {
                LIST_CALLS.incrementAndGet();
                return List.of(
                        new LlmModelOption("claude-opus-5", "Claude Opus 5"),
                        new LlmModelOption("claude-haiku-4-5-20251001", "Claude Haiku 4.5"));
            };
        }
    }

    @Autowired private LlmModelService llmModelService;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private CachePort cache;

    @BeforeEach
    void reset() {
        LIST_CALLS.set(0);
        // 앞 시험이 고른 값을 물려받지 않는다
        llmModelService.update(List.of(new UpdateLlmModelRequest(
                LlmFeature.RECOMMENDATION.configKey(), "")));
    }

    @Test
    @DisplayName("아무것도 안 고르면 기본 모델을 쓴다")
    void fallsBackToTheDefault() {
        assertThat(llmModelService.modelFor(LlmFeature.RECOMMENDATION))
                .as("빈 값을 그대로 실으면 400이 오고, 그 실패는 'AI가 답을 안 줬다'로 묻힌다")
                .isNotBlank();
    }

    @Test
    @DisplayName("고른 모델이 그 자리에만 적용된다")
    void appliesOnlyToTheChosenFeature() {
        final String before = llmModelService.modelFor(LlmFeature.COMPARATIVE);

        llmModelService.update(List.of(new UpdateLlmModelRequest(
                LlmFeature.RECOMMENDATION.configKey(), "claude-haiku-4-5-20251001")));

        assertThat(llmModelService.modelFor(LlmFeature.RECOMMENDATION))
                .isEqualTo("claude-haiku-4-5-20251001");
        assertThat(llmModelService.modelFor(LlmFeature.COMPARATIVE))
                .as("한 자리를 고쳤는데 다른 자리까지 바뀌었다")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("목록에 없는 모델은 받지 않는다")
    void rejectsUnknownModels() {
        assertThatThrownBy(() -> llmModelService.update(List.of(new UpdateLlmModelRequest(
                LlmFeature.RECOMMENDATION.configKey(), "claude-지어낸-이름"))))
                .as("아무 문자열이나 받으면 그 자리의 AI가 조용히 죽는다")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AI 모델 설정이 아닌 키는 이 길로 못 고친다")
    void rejectsForeignConfigKeys() {
        assertThatThrownBy(() -> llmModelService.update(List.of(
                new UpdateLlmModelRequest("loan.regulation.profile", "claude-opus-5"))))
                .as("임의의 설정 키를 고칠 수 있으면 규제 파라미터까지 바뀐다")
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(systemConfigRepository.findById("loan.regulation.profile")
                .orElseThrow().configValue())
                .isEqualTo("2025-10-15");
    }
}
