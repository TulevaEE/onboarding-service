package ee.tuleva.onboarding.payment.savings;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("payment-provider.savings-fund")
@Data
public class SavingsFundRecipientConfiguration {
  private @Nullable String recipientName;
  private @Nullable String recipientIban;

  public String getRecipientName() {
    return requireNonNull(recipientName, "Missing payment-provider.savings-fund.recipient-name");
  }

  public String getRecipientIban() {
    return requireNonNull(recipientIban, "Missing payment-provider.savings-fund.recipient-iban");
  }

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
