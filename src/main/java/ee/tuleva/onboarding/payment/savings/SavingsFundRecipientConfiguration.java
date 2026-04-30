package ee.tuleva.onboarding.payment.savings;

import static org.apache.commons.lang3.StringUtils.isBlank;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("payment-provider.savings-fund")
@Data
public class SavingsFundRecipientConfiguration {
  private String recipientName;
  private String recipientIban;

  @PostConstruct
  void validate() {
    if (isBlank(recipientName)) {
      throw new IllegalStateException("Missing payment-provider.savings-fund.recipient-name");
    }
    if (isBlank(recipientIban)) {
      throw new IllegalStateException("Missing payment-provider.savings-fund.recipient-iban");
    }
  }
}
