package ee.tuleva.onboarding.payment.savings;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("payment-provider.savings-fund")
@Data
public class SavingsFundRecipientConfiguration {
  private String recipientName;
  private String recipientIban;
}
