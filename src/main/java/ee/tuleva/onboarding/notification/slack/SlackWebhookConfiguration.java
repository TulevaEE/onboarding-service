package ee.tuleva.onboarding.notification.slack;

import ee.tuleva.onboarding.notification.slack.SlackService.SlackChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "slack")
class SlackWebhookConfiguration {
  
  private final Map<String, String> webhooks;


  public String getWebhookUrl(SlackChannel channel) {
    return webhooks.getOrDefault(channel.getConfigurationKey(), null);
  }
}
