package banghak.home.halley.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "slack")
public class SlackProperties {
    private boolean enabled = false;
 /** 매물 등록 알림만 스위치가 따로 있습니다. */
    private boolean notifyPropertyCreated = false;
}
