package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP_WEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.PARTNER;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SWEDBANK;

import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("payment-provider.third-pillar")
@Data
public class ThirdPillarRecipientConfiguration {

  private static final Set<PaymentChannel> REQUIRED_CHANNELS =
      Set.of(LHV, SEB, SWEDBANK, COOP, COOP_WEB, PARTNER);

  private String recipientName;
  private String description;
  private Map<PaymentChannel, String> bankAccounts;

  @PostConstruct
  void validate() {
    var missing =
        REQUIRED_CHANNELS.stream()
            .filter(c -> bankAccounts == null || !bankAccounts.containsKey(c))
            .toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Missing payment-provider.third-pillar.bank-accounts entries: " + missing);
    }
  }
}
