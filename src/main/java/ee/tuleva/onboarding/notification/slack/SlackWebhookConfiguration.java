package ee.tuleva.onboarding.notification.slack;

import ee.tuleva.onboarding.notification.slack.SlackService.SlackChannel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "slack")
@Getter
@Setter
public class SlackWebhookConfiguration {

  private Map<String, String> webhooks;

  public String getWebhookUrl(SlackChannel channel) {
    return webhooks.getOrDefault(channel.getConfigurationKey(), null);
  }
}
