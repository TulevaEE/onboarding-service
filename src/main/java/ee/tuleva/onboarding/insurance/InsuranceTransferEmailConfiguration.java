package ee.tuleva.onboarding.insurance;

import ee.tuleva.onboarding.notification.email.AtMostOnceEmailSender;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
class InsuranceTransferEmailConfiguration {

  @Bean
  AtMostOnceEmailSender insurerRequestEmailTransport(
      @Value("${mandrill.key:#{null}}") @Nullable String mandrillKey,
      @Value("${mandrill.url}") String mandrillApiUrl) {
    if (mandrillKey == null || mandrillKey.isBlank()) {
      log.info("Mandrill key not present, signed insurance transfer forms cannot be emailed");
      return AtMostOnceEmailSender.withoutKey();
    }
    return AtMostOnceEmailSender.using(mandrillKey, URI.create(mandrillApiUrl));
  }
}
