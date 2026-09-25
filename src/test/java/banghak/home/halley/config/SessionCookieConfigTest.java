package banghak.home.halley.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 쿠키 플래그와 프록시 헤더 (설계 I297).
 *
 * <p>live 프로필은 DB 자격을 강제해 시험에서 띄울 수 없다(설계 I296). 그래서 설정 파일을
 * 그대로 읽어 <b>적혀 있는지</b>만 지킨다 — 빠지면 HTTPS 뒤에서도 Secure 쿠키가 안 나간다.
 */
@DisplayName("세션 쿠키 · 프록시 설정 (설계 I297)")
class SessionCookieConfigTest {

    private static Properties yaml(String path) {
        final YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource(path));
        return factory.getObject();
    }

    @Test
    @DisplayName("기본 설정: 세션 쿠키는 SameSite=Lax · HttpOnly")
    void baseCookieFlags() {
        // given
        final Properties base = yaml("src/main/resources/application.yaml");

        // then
        assertThat(base.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        assertThat(base.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
    }

    @Test
    @DisplayName("live: Secure 쿠키 · X-Forwarded-* 신뢰")
    void liveCookieFlagsAndProxy() {
        // given
        final Properties live = yaml("src/main/resources/application-live.yaml");

        // then
        assertThat(live.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(live.getProperty("server.forward-headers-strategy")).isEqualTo("native");
    }
}
