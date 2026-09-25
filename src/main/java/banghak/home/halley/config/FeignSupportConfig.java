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

    @Bean
    public CircuitBreakerNameResolver feignClientNameResolver() {
        return (String feignClientName, Target<?> target, Method method) -> feignClientName;
    }
}
