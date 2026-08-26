package banghak.home.halley.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "banghak.home.halley.adapter.outbound.external")
public class FeignSupportConfig {
}
