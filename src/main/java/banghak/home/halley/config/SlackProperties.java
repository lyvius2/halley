package banghak.home.halley.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "slack")
public class SlackProperties {
    private boolean enabled = false;
    private String webhookUrl = "";
    private boolean notifyPropertyCreated = false;
}
