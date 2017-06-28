package ee.tuleva.onboarding.notification.mailchimp;

import com.ecwid.maleorang.MailchimpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailChimpConfig {

  @Value("${mailchimp.api.key}")
  private String apiKey;

  @Bean
  MailchimpClient mailchimpClient() {
    return new MailchimpClient(apiKey);
  }

}
