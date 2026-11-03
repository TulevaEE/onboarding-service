package ee.tuleva.onboarding.investment.transaction.export;

import ee.tuleva.onboarding.notification.email.AtMostOnceEmailSender;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
class CustodianOrderEmailConfiguration {

  @Bean
  AtMostOnceEmailSender custodianOrderEmailTransport(
      @Value("${mandrill.order-key:#{null}}") @Nullable String orderKey,
      @Value("${mandrill.url}") String mandrillApiUrl) {
    if (orderKey == null || orderKey.isBlank()) {
      log.info("Mandrill order email key not present, order emails cannot be sent");
      return AtMostOnceEmailSender.withoutKey();
    }
    return AtMostOnceEmailSender.using(orderKey, URI.create(mandrillApiUrl));
  }
}
