package banghak.home.halley.config;

import feign.Target;
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

@Configuration
@EnableFeignClients(basePackages = "banghak.home.halley.adapter.outbound.external")
public class FeignSupportConfig {

 /** 서킷브레이커·TimeLimiter의 인스턴스 이름을 @FeignClient의 name으로 맞춘다. */
    @Bean
    public CircuitBreakerNameResolver feignClientNameResolver() {
        return (String feignClientName, Target<?> target, Method method) -> feignClientName;
    }
}
