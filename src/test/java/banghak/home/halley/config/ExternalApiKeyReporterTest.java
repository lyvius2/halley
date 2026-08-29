package banghak.home.halley.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalApiKeyReporterTest {

    @Test
    @DisplayName("키가 비어 있으면 미설정으로 표시해 빈 결과의 원인을 드러낸다")
    void reportsMissingKey() {
        // then
        assertThat(ExternalApiKeyReporter.mask(null)).startsWith("NOT SET");
        assertThat(ExternalApiKeyReporter.mask("   ")).startsWith("NOT SET");
    }

    @Test
    @DisplayName("설정된 키는 앞뒤 4자만 남기고 마스킹한다")
    void masksConfiguredKey() {
        // when
        final String masked = ExternalApiKeyReporter.mask("7fd1d10f70f54d49a25555e4e7f761ec");

        // then
        assertThat(masked).isEqualTo("set (7fd1****61ec)");
        assertThat(masked).doesNotContain("d10f70f54d49");
    }

    @Test
    @DisplayName("8자 이하의 짧은 값은 일부도 노출하지 않는다")
    void masksShortKeyEntirely() {
        // then
        assertThat(ExternalApiKeyReporter.mask("abcd1234")).isEqualTo("set (****)");
    }
}
