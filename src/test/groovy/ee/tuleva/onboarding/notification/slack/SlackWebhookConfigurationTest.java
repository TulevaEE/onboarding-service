package ee.tuleva.onboarding.notification.slack;

import static ee.tuleva.onboarding.notification.slack.SlackService.SlackChannel.AML;
import static ee.tuleva.onboarding.notification.slack.SlackService.SlackChannel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SlackWebhookConfigurationTest {

  @Test
  void returnsTheConfiguredWebhookUrlForAChannel() {
    SlackWebhookConfiguration configuration = new SlackWebhookConfiguration();
    configuration.setWebhooks(Map.of("aml", "https://example.com/aml-webhook"));

    assertThat(configuration.getWebhookUrl(AML)).isEqualTo("https://example.com/aml-webhook");
  }

  @Test
  void returnsNullWhenNoWebhookIsConfiguredForAChannel() {
    SlackWebhookConfiguration configuration = new SlackWebhookConfiguration();
    configuration.setWebhooks(Map.of("aml", "https://example.com/aml-webhook"));

    assertThat(configuration.getWebhookUrl(INVESTMENT)).isNull();
  }
}
