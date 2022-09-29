package ee.tuleva.onboarding.payment.provider;

import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConfigurationProperties("payment-provider")
class PaymentProviderConfiguration {

  @Getter private final Map<Bank, PaymentProviderBank> banks;
  private Map<String, PaymentProviderBank> banksByAccessKey;

  public PaymentProviderBank getPaymentProviderBank(Bank bank) {
    return banks.get(bank);
  }

  public PaymentProviderBank getPaymentProviderBank(String accessKey) {
    return banksByAccessKey.get(accessKey);
  }

  @PostConstruct
  private void mapByAccessKey() {
    banksByAccessKey =
        banks.entrySet().stream()
            .collect(toMap(entry -> entry.getValue().accessKey, Entry::getValue));
  }
}
