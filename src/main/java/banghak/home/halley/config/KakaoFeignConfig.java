package banghak.home.halley.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class KakaoFeignConfig {

    @Bean
    public RequestInterceptor kakaoAuthInterceptor(@Value("${kakao.rest-key:}") String restKey) {
        return template -> template.header("Authorization", "KakaoAK " + restKey);
    }
}
