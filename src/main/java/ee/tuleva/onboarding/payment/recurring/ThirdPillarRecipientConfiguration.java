package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP_WEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.PARTNER;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SWEDBANK;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("payment-provider.third-pillar")
@Data
public class ThirdPillarRecipientConfiguration {

  private static final Set<PaymentChannel> REQUIRED_CHANNELS =
      Set.of(LHV, SEB, SWEDBANK, COOP, COOP_WEB, PARTNER);

  private @Nullable String recipientName;
  private @Nullable String description;
  private @Nullable Map<PaymentChannel, String> bankAccounts;

  public String getRecipientName() {
    return requireNonNull(recipientName, "Missing payment-provider.third-pillar.recipient-name");
  }

  public String getDescription() {
    return requireNonNull(description, "Missing payment-provider.third-pillar.description");
  }

  public Map<PaymentChannel, String> getBankAccounts() {
    return requireNonNull(bankAccounts, "Missing payment-provider.third-pillar.bank-accounts");
  }

  @PostConstruct
  void validate() {
    if (isBlank(recipientName)) {
      throw new IllegalStateException("Missing payment-provider.third-pillar.recipient-name");
    }
    if (isBlank(description)) {
      throw new IllegalStateException("Missing payment-provider.third-pillar.description");
    }
    var invalid =
        REQUIRED_CHANNELS.stream()
            .filter(
                c ->
                    bankAccounts == null
                        || !bankAccounts.containsKey(c)
                        || isBlank(bankAccounts.get(c)))
            .toList();
    if (!invalid.isEmpty()) {
      throw new IllegalStateException(
          "Missing or blank payment-provider.third-pillar.bank-accounts entries: " + invalid);
    }
  }
}
